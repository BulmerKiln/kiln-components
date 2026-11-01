/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration;

import com.bulmerkiln.scan.constant.Severity;
import com.bulmerkiln.scan.domain.Finding;
import com.bulmerkiln.scan.domain.ScanResult;
import com.bulmerkiln.scan.domain.ScannerId;
import com.bulmerkiln.scan.orchestration.domain.MergedFinding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Merges the results of several scanners for one job into one list (SCAN-ORCH-1). Two findings are
 * the same issue when they're of the same kind, affect the same package, and share any identifier,
 * directly or through other findings' aliases.
 */
public final class FindingMerger {

    private static final List<String> PREFERRED_PREFIXES = List.of("CVE-", "GHSA-", "MAL-");

    private FindingMerger() {}

    /**
     * Merges results, ordered by severity and then identifier.
     *
     * @throws IllegalArgumentException if the results belong to different jobs
     */
    public static List<MergedFinding> merge(List<ScanResult> results) {
        if (results.stream().map(ScanResult::getJobId).distinct().count() > 1) {
            throw new IllegalArgumentException("Results from different jobs can't be merged");
        }
        Set<ScannerId> ran = new TreeSet<>();
        Map<GroupKey, List<Reported>> byPackage = new HashMap<>();
        for (ScanResult result : results) {
            if (result.getStatus() == ScanResult.Status.FAILED) {
                continue;
            }
            ran.add(result.getScanner());
            for (Finding finding : result.getFindings()) {
                byPackage
                        .computeIfAbsent(
                                new GroupKey(finding.getKind(), packageKey(finding.getPurl())), k -> new ArrayList<>())
                        .add(new Reported(result.getScanner(), finding));
            }
        }
        List<MergedFinding> merged = new ArrayList<>();
        for (List<Reported> reports : byPackage.values()) {
            for (List<Reported> group : groupByIdentifier(reports)) {
                merged.add(combine(group, ran.size()));
            }
        }
        merged.sort(Comparator.comparing(MergedFinding::getSeverity).thenComparing(MergedFinding::getId));
        return List.copyOf(merged);
    }

    /** Union-find over identifiers, so A~B and B~C put A, B and C in one group. */
    private static List<List<Reported>> groupByIdentifier(List<Reported> reports) {
        Map<String, String> parent = new HashMap<>();
        for (Reported report : reports) {
            String first = report.getFinding().getId();
            for (String identifier : identifiers(report.getFinding())) {
                union(parent, first, identifier);
            }
        }
        Map<String, List<Reported>> groups = new HashMap<>();
        for (Reported report : reports) {
            groups.computeIfAbsent(find(parent, report.getFinding().getId()), k -> new ArrayList<>())
                    .add(report);
        }
        return List.copyOf(groups.values());
    }

    private static MergedFinding combine(List<Reported> group, int scannersRun) {
        Set<String> identifiers = new TreeSet<>();
        Set<ScannerId> reportedBy = new TreeSet<>();
        Severity severity = Severity.UNKNOWN;
        // Fixed version and title come from the first reporter in scanner ID order, so output is stable.
        group.sort(Comparator.comparing(Reported::getScanner));
        String fixedVersion = null;
        String title = null;
        for (Reported report : group) {
            identifiers.addAll(identifiers(report.getFinding()));
            reportedBy.add(report.getScanner());
            severity = severity.max(report.getFinding().getSeverity());
            fixedVersion =
                    fixedVersion != null ? fixedVersion : report.getFinding().getFixedVersion();
            title = title != null ? title : report.getFinding().getTitle();
        }
        String id = preferred(identifiers);
        identifiers.remove(id);
        Finding first = group.getFirst().getFinding();
        return new MergedFinding(
                first.getKind(),
                id,
                identifiers,
                first.getPurl(),
                severity,
                fixedVersion,
                title,
                reportedBy,
                scannersRun);
    }

    private static String preferred(Set<String> identifiers) {
        for (String prefix : PREFERRED_PREFIXES) {
            for (String identifier : identifiers) {
                if (identifier.startsWith(prefix)) {
                    return identifier;
                }
            }
        }
        return identifiers.iterator().next();
    }

    private static Set<String> identifiers(Finding finding) {
        Set<String> all = new LinkedHashSet<>();
        all.add(finding.getId());
        all.addAll(finding.getAliases());
        return all;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    private static String find(Map<String, String> parent, String identifier) {
        String root = identifier;
        while (parent.containsKey(root) && !parent.get(root).equals(root)) {
            root = Objects.requireNonNull(parent.get(root));
        }
        parent.putIfAbsent(identifier, root);
        return root;
    }

    /**
     * The package a Package URL names, without qualifiers or subpath: Trivy and Grype can write
     * {@code ?arch=amd64&distro=debian-12} differently for the same package.
     */
    static @Nullable String packageKey(@Nullable String purl) {
        if (purl == null) {
            return null;
        }
        int query = purl.indexOf('?');
        int fragment = purl.indexOf('#');
        int end = purl.length();
        if (query >= 0) {
            end = query;
        }
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        return purl.substring(0, end);
    }

    @Value
    private static final class GroupKey {

        private final Finding.Kind kind;

        private final @Nullable String purl;
    }

    @Value
    private static final class Reported {

        private final ScannerId scanner;

        private final Finding finding;
    }
}
