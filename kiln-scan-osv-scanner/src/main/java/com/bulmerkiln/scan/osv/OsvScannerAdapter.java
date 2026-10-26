/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.osv;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanJob;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerId;
import com.bulmerkiln.scan.Severities;
import com.bulmerkiln.scan.Severity;
import com.bulmerkiln.scan.ToolOutput;
import com.bulmerkiln.scan.ToolRun;
import com.bulmerkiln.scan.ToolText;
import com.bulmerkiln.scan.ToolVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * OSV-Scanner, matching the stored SBOM against the OSV database. The OSV
 * database includes OpenSSF's malicious-packages feed, so {@code MAL-} entries are reported here,
 * as malware. Exits 1 when it finds anything.
 */
public final class OsvScannerAdapter implements ScannerAdapter {

    public static final ScannerId ID = new ScannerId("osv-scanner");

    private static final ToolVersion MINIMUM = new ToolVersion(2, 7, 0);

    @Override
    public ScannerId scanner() {
        return ID;
    }

    @Override
    public boolean supportsVersion(String toolVersion) {
        return ToolVersion.supported(toolVersion, MINIMUM, 0);
    }

    @Override
    public boolean completed(int exitCode) {
        return exitCode == 0 || exitCode == 1;
    }

    @Override
    public List<String> command(ScanJob job, Path input) {
        return List.of(
                "/usr/local/bin/osv-scanner",
                "scan",
                "source",
                "-L",
                input.toString(),
                "--format",
                "json",
                "--offline");
    }

    @Override
    public List<Finding> parse(ScanJob job, ToolRun run) {
        JsonNode root = ToolOutput.json(run.output());
        JsonNode results = root.path("results");
        if (!root.isObject() || !results.isArray()) {
            throw new IllegalArgumentException("Not an OSV-Scanner report");
        }
        List<Finding> findings = new ArrayList<>();
        for (JsonNode result : results) {
            for (JsonNode pkg : list(result.path("packages"))) {
                Map<String, Severity> groupSeverity = groupSeverities(pkg.path("groups"));
                String purl = purl(pkg.path("package"));
                for (JsonNode vulnerability : list(pkg.path("vulnerabilities"))) {
                    findings.add(finding(vulnerability, purl, groupSeverity));
                }
            }
        }
        return findings;
    }

    private static Finding finding(JsonNode vulnerability, @Nullable String purl, Map<String, Severity> groupSeverity) {
        String id = vulnerability.required("id").asString();
        Set<String> aliases = new LinkedHashSet<>();
        for (JsonNode alias : list(vulnerability.path("aliases"))) {
            if (alias.isString() && !alias.asString().equals(id)) {
                aliases.add(alias.asString());
            }
        }
        boolean malicious = id.startsWith("MAL-");
        Severity severity = malicious
                ? Severity.CRITICAL
                : groupSeverity.getOrDefault(
                        id,
                        Severities.named(vulnerability
                                .path("database_specific")
                                .path("severity")
                                .asString(null)));
        return new Finding(
                malicious ? Finding.Kind.MALWARE : Finding.Kind.VULNERABILITY,
                id,
                List.copyOf(aliases),
                purl,
                severity,
                fixedVersion(vulnerability),
                ToolText.clean(vulnerability.path("summary").asString(null), 512));
    }

    /** The CVSS score OSV-Scanner computed for each group of aliases, as {@code max_severity}. */
    private static Map<String, Severity> groupSeverities(JsonNode groups) {
        Map<String, Severity> severities = new HashMap<>();
        for (JsonNode group : list(groups)) {
            Severity severity;
            try {
                severity = Severities.cvss(
                        Double.parseDouble(group.path("max_severity").asString("")));
            } catch (NumberFormatException e) {
                continue;
            }
            if (severity == Severity.UNKNOWN) {
                continue;
            }
            for (JsonNode id : list(group.path("ids"))) {
                severities.put(id.asString(""), severity);
            }
        }
        return severities;
    }

    private static @Nullable String fixedVersion(JsonNode vulnerability) {
        for (JsonNode affected : list(vulnerability.path("affected"))) {
            for (JsonNode range : list(affected.path("ranges"))) {
                for (JsonNode event : list(range.path("events"))) {
                    if (event.path("fixed").isString()) {
                        return ToolText.clean(event.path("fixed").asString(), 255);
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable String purl(JsonNode pkg) {
        JsonNode purl = pkg.path("purl");
        return purl.isString() && purl.asString().startsWith("pkg:") ? purl.asString() : null;
    }

    private static Iterable<JsonNode> list(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected a list");
        }
        return node;
    }
}
