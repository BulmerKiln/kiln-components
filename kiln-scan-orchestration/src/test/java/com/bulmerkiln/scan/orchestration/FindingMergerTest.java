/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanResult;
import com.bulmerkiln.scan.ScannerId;
import com.bulmerkiln.scan.Severity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindingMergerTest {

    static final ScannerId GITLEAKS = new ScannerId("gitleaks");
    static final ScannerId GRYPE = new ScannerId("grype");
    static final ScannerId MALICIOUS_PACKAGES = new ScannerId("malicious-packages");
    static final ScannerId OSV_SCANNER = new ScannerId("osv-scanner");
    static final ScannerId TRIVY = new ScannerId("trivy");

    private static final UUID JOB = UUID.randomUUID();
    private static final String XZ = "pkg:deb/debian/xz-utils@5.6.0";

    @Test
    void mergesTheSameIssueAcrossScannersByAlias() {
        List<MergedFinding> merged = FindingMerger.merge(List.of(
                result(TRIVY, vuln("CVE-2024-3094", XZ, Severity.CRITICAL)),
                result(GRYPE, vuln("GHSA-rxwq-x6h5-x525", XZ, Severity.HIGH, "CVE-2024-3094")),
                result(OSV_SCANNER, vuln("OSV-2024-1", XZ, Severity.HIGH, "GHSA-rxwq-x6h5-x525"))));

        assertThat(merged).singleElement().satisfies(f -> {
            assertThat(f.id()).isEqualTo("CVE-2024-3094");
            assertThat(f.aliases()).containsExactlyInAnyOrder("GHSA-rxwq-x6h5-x525", "OSV-2024-1");
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.reportedBy()).containsExactlyInAnyOrder(TRIVY, GRYPE, OSV_SCANNER);
            assertThat(f.reportedByAll()).isTrue();
        });
    }

    @Test
    void keepsAFindingOnlyOneEngineReported() {
        List<MergedFinding> merged = FindingMerger.merge(
                List.of(result(TRIVY, vuln("CVE-2024-1", XZ, Severity.MEDIUM)), result(GRYPE), result(OSV_SCANNER)));

        assertThat(merged).singleElement().satisfies(f -> {
            assertThat(f.reportedBy()).containsExactly(TRIVY);
            assertThat(f.scannersRun()).isEqualTo(3);
            assertThat(f.reportedByAll()).isFalse();
        });
    }

    @Test
    void doesNotMergeTheSameIdOnDifferentPackagesOrKinds() {
        List<MergedFinding> merged = FindingMerger.merge(List.of(result(
                TRIVY,
                vuln("CVE-2024-1", "pkg:npm/a@1.0.0", Severity.LOW),
                vuln("CVE-2024-1", "pkg:npm/b@1.0.0", Severity.LOW),
                new Finding(
                        Finding.Kind.MISCONFIGURATION,
                        "CVE-2024-1",
                        List.of(),
                        "pkg:npm/a@1.0.0",
                        Severity.LOW,
                        null,
                        null))));
        assertThat(merged).hasSize(3);
    }

    @Test
    void failedScansAreNotCountedAsRun() {
        ScanResult failed = new ScanResult(1, JOB, GRYPE, "0.80", "db", ScanResult.Status.FAILED, null, List.of());
        List<MergedFinding> merged =
                FindingMerger.merge(List.of(result(TRIVY, vuln("CVE-1", XZ, Severity.LOW)), failed));
        assertThat(merged.getFirst().scannersRun()).isEqualTo(1);
    }

    @Test
    void incompleteScansStillContributeFindings() {
        ScanResult incomplete = new ScanResult(
                1,
                JOB,
                GITLEAKS,
                "8.18",
                "rules-1",
                ScanResult.Status.INCOMPLETE,
                "max files",
                List.of(new Finding(
                        Finding.Kind.SECRET, "aws-access-token", List.of(), null, Severity.HIGH, null, "AWS key")));
        assertThat(FindingMerger.merge(List.of(incomplete)))
                .singleElement()
                .extracting(MergedFinding::kind)
                .isEqualTo(Finding.Kind.SECRET);
    }

    @Test
    void prefersCveThenGhsaThenMalware() {
        assertThat(FindingMerger.merge(
                                List.of(result(OSV_SCANNER, vuln("OSV-1", XZ, Severity.LOW, "GHSA-aaaa-bbbb-cccc"))))
                        .getFirst()
                        .id())
                .isEqualTo("GHSA-aaaa-bbbb-cccc");
        Finding malware = new Finding(
                Finding.Kind.MALWARE,
                "MAL-2024-100",
                List.of("ZZZ-1"),
                "pkg:npm/evil@1.0.0",
                Severity.CRITICAL,
                null,
                null);
        assertThat(FindingMerger.merge(List.of(result(MALICIOUS_PACKAGES, malware)))
                        .getFirst()
                        .id())
                .isEqualTo("MAL-2024-100");
    }

    @Test
    void ordersBySeverityThenId() {
        List<MergedFinding> merged = FindingMerger.merge(List.of(result(
                TRIVY,
                vuln("CVE-3", "pkg:npm/c@1", Severity.LOW),
                vuln("CVE-2", "pkg:npm/b@1", Severity.CRITICAL),
                vuln("CVE-1", "pkg:npm/a@1", Severity.CRITICAL))));
        assertThat(merged).extracting(MergedFinding::id).containsExactly("CVE-1", "CVE-2", "CVE-3");
    }

    @Test
    void isDeterministicWhateverTheInputOrder() {
        List<ScanResult> results = new ArrayList<>(List.of(
                result(TRIVY, vuln("CVE-1", XZ, Severity.HIGH)),
                new ScanResult(
                        1,
                        JOB,
                        GRYPE,
                        "0.80",
                        "db",
                        ScanResult.Status.COMPLETE,
                        null,
                        List.of(new Finding(
                                Finding.Kind.VULNERABILITY,
                                "CVE-1",
                                List.of(),
                                XZ,
                                Severity.LOW,
                                "9.9.9",
                                "Other title")))));
        MergedFinding forward = FindingMerger.merge(results).getFirst();
        Collections.reverse(results);
        MergedFinding reversed = FindingMerger.merge(results).getFirst();
        assertThat(reversed).isEqualTo(forward);
        assertThat(forward.fixedVersion()).as("from grype, first by scanner ID").isEqualTo("9.9.9");
    }

    @Test
    void refusesResultsFromDifferentJobs() {
        ScanResult other =
                new ScanResult(1, UUID.randomUUID(), GRYPE, "0.80", "db", ScanResult.Status.COMPLETE, null, List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> FindingMerger.merge(List.of(result(TRIVY), other)));
    }

    @Test
    void handlesLargeAliasChainsQuickly() {
        List<Finding> chain = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            chain.add(vuln("ID-" + i, XZ, Severity.LOW, "ID-" + (i + 1)));
        }
        long start = System.nanoTime();
        List<MergedFinding> merged = FindingMerger.merge(List.of(result(OSV_SCANNER, chain.toArray(Finding[]::new))));
        assertThat(merged).singleElement().extracting(f -> f.aliases().size()).isEqualTo(5_000);
        assertThat(System.nanoTime() - start).isLessThan(5_000_000_000L);
    }

    @Test
    void countsBySeverity() {
        SeverityCounts counts = SeverityCounts.of(FindingMerger.merge(List.of(result(
                TRIVY,
                vuln("CVE-1", "pkg:npm/a@1", Severity.CRITICAL),
                vuln("CVE-2", "pkg:npm/b@1", Severity.CRITICAL),
                vuln("CVE-3", "pkg:npm/c@1", Severity.LOW)))));
        assertThat(counts.get(Severity.CRITICAL)).isEqualTo(2);
        assertThat(counts.get(Severity.LOW)).isEqualTo(1);
        assertThat(counts.get(Severity.HIGH)).isZero();
        assertThat(counts.counts()).hasSize(Severity.values().length);
    }

    @Test
    void mergesNothingToNothing() {
        assertThat(FindingMerger.merge(List.of())).isEmpty();
        assertThat(SeverityCounts.of(List.of()).get(Severity.CRITICAL)).isZero();
    }

    private static Finding vuln(String id, String purl, Severity severity, String... aliases) {
        return new Finding(Finding.Kind.VULNERABILITY, id, List.of(aliases), purl, severity, "1.2.4", "Title");
    }

    private static ScanResult result(ScannerId scanner, Finding... findings) {
        return new ScanResult(1, JOB, scanner, "1.0", "db", ScanResult.Status.COMPLETE, null, List.of(findings));
    }

    @Test
    void mergesAcrossScannersThatWritePurlQualifiersDifferently() {
        List<MergedFinding> merged = FindingMerger.merge(List.of(
                result(
                        TRIVY,
                        vuln(
                                "CVE-2024-3094",
                                "pkg:deb/debian/xz-utils@5.6.0?arch=amd64&distro=debian-12",
                                Severity.CRITICAL)),
                result(
                        GRYPE,
                        vuln(
                                "CVE-2024-3094",
                                "pkg:deb/debian/xz-utils@5.6.0?distro=debian-12&arch=amd64",
                                Severity.CRITICAL)),
                result(OSV_SCANNER, vuln("CVE-2024-3094", "pkg:deb/debian/xz-utils@5.6.0", Severity.HIGH))));
        assertThat(merged)
                .singleElement()
                .satisfies(f -> assertThat(f.reportedBy()).hasSize(3));
    }

    @Test
    void stillSeparatesDifferentVersions() {
        List<MergedFinding> merged = FindingMerger.merge(List.of(result(
                TRIVY,
                vuln("CVE-2024-3094", "pkg:deb/debian/xz-utils@5.6.0?arch=amd64", Severity.CRITICAL),
                vuln("CVE-2024-3094", "pkg:deb/debian/xz-utils@5.6.1?arch=amd64", Severity.CRITICAL))));
        assertThat(merged).hasSize(2);
    }

    @Test
    void packageKeysDropOnlyQualifiersAndSubpaths() {
        assertThat(FindingMerger.packageKey("pkg:npm/%40acme/ui@1.0.0?x=1#sub")).isEqualTo("pkg:npm/%40acme/ui@1.0.0");
        assertThat(FindingMerger.packageKey("pkg:npm/a@1#sub?x")).isEqualTo("pkg:npm/a@1");
        assertThat(FindingMerger.packageKey("pkg:npm/a@1")).isEqualTo("pkg:npm/a@1");
        assertThat(FindingMerger.packageKey(null)).isNull();
    }
}
