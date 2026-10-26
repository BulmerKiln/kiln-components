/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.scan.Limits;
import com.bulmerkiln.scan.ScanJob;
import com.bulmerkiln.scan.ScanResult;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerAdapters;
import com.bulmerkiln.scan.Severity;
import com.bulmerkiln.scan.ToolRun;
import com.bulmerkiln.scan.grype.GrypeAdapter;
import com.bulmerkiln.scan.osv.OsvScannerAdapter;
import com.bulmerkiln.scan.trivy.TrivyAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Three real scans of the same SBOM (lodash 4.17.20), merged as the server would. */
class RecordedScansTest {

    private static final ScanJob JOB = new ScanJob(
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Digest.sha256(new byte[0]),
            "application/vnd.cyclonedx+json",
            URI.create("https://pkg.example/x"),
            Instant.now(),
            List.of(TrivyAdapter.ID, GrypeAdapter.ID, OsvScannerAdapter.ID),
            new Limits(1L << 30, 1000, 8, 60));

    @Test
    void theSameAdvisoriesFromThreeEnginesMergeIntoOneEach() throws IOException {
        List<MergedFinding> merged = FindingMerger.merge(List.of(
                scan(new TrivyAdapter(), "trivy-0.76.1-vulnerable.json", "0.76.1", 0),
                scan(new GrypeAdapter(), "grype-0.121.0-vulnerable.json", "0.121.0", 0),
                scan(new OsvScannerAdapter(), "osv-scanner-2.7.0-vulnerable.json", "2.7.0", 1)));

        // Trivy names each by CVE with a GHSA alias; Grype and OSV-Scanner the other way round.
        assertThat(merged).hasSize(5).allSatisfy(f -> {
            assertThat(f.id()).startsWith("CVE-");
            assertThat(f.aliases()).anyMatch(a -> a.startsWith("GHSA-"));
            assertThat(f.reportedBy())
                    .containsExactlyInAnyOrder(TrivyAdapter.ID, GrypeAdapter.ID, OsvScannerAdapter.ID);
            assertThat(f.reportedByAll()).isTrue();
            assertThat(f.fixedVersion()).isNotNull();
        });
        assertThat(merged.stream()
                        .filter(f -> f.id().equals("CVE-2021-23337"))
                        .findFirst()
                        .orElseThrow()
                        .severity())
                .isEqualTo(Severity.HIGH);
        assertThat(SeverityCounts.of(merged).get(Severity.HIGH)).isEqualTo(2);
        assertThat(SeverityCounts.of(merged).get(Severity.MEDIUM)).isEqualTo(3);
    }

    private ScanResult scan(ScannerAdapter adapter, String recording, String version, int exitCode) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(recording)) {
            String output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ScanResult result =
                    ScannerAdapters.run(adapter, JOB, new ToolRun(version, "db", exitCode, output, false, null));
            assertThat(result.status()).isEqualTo(ScanResult.Status.COMPLETE);
            return result;
        }
    }
}
