/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.digest.Digest;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScanProtocolTest {

    static final ScannerId GRYPE = new ScannerId("grype");
    static final ScannerId SYFT = new ScannerId("syft");
    static final ScannerId TRIVY = new ScannerId("trivy");

    static final UUID JOB = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    static Finding cve(String id, String purl, Severity severity, String... aliases) {
        return new Finding(
                Finding.Kind.VULNERABILITY, id, List.of(aliases), purl, severity, "1.2.4", "Buffer overflow");
    }

    static ScanResult result(ScannerId scanner, Finding... findings) {
        return new ScanResult(
                1, JOB, scanner, "0.55.0", "2026-10-26T00:00:00Z", ScanResult.Status.COMPLETE, null, List.of(findings));
    }

    @Test
    void jobsRoundTrip() {
        ScanJob job = new ScanJob(
                1,
                JOB,
                UUID.randomUUID(),
                Digest.sha256(new byte[0]),
                "application/vnd.oci.image.manifest.v1+json",
                URI.create("https://pkg.example/presigned?sig=x"),
                Instant.parse("2026-10-26T12:00:00Z"),
                List.of(SYFT, TRIVY),
                new Limits(10L << 30, 100_000, 32, 900));
        assertThat(ScanProtocol.readJob(ScanProtocol.write(job))).isEqualTo(job);
    }

    @Test
    void resultsRoundTrip() {
        ScanResult result = result(
                GRYPE, cve("CVE-2024-3094", "pkg:deb/debian/xz-utils@5.6.0", Severity.CRITICAL, "GHSA-rxwq-x6h5-x525"));
        assertThat(ScanProtocol.readResult(ScanProtocol.write(result))).isEqualTo(result);
    }

    @Test
    void ignoresUnknownFieldsFromNewerMinorVersions() {
        String json = new String(ScanProtocol.write(result(TRIVY)), StandardCharsets.UTF_8)
                .replaceFirst("\\{", "{\"futureField\":1,");
        assertThat(ScanProtocol.readResult(json.getBytes(StandardCharsets.UTF_8))
                        .scanner())
                .isEqualTo(TRIVY);
    }

    @Test
    void refusesOtherProtocolVersions() {
        String json = new String(ScanProtocol.write(result(TRIVY)), StandardCharsets.UTF_8)
                .replace("\"protocol\":1", "\"protocol\":2");
        assertThatThrownBy(() -> ScanProtocol.readResult(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ScanProtocol.UnsupportedProtocolException.class)
                .hasMessageContaining("version 2");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "[]",
                "{}",
                "{\"protocol\":1}",
                "{\"protocol\":1,\"protocol\":1}",
                "{\"protocol\":\"1\",\"jobId\":\"0f8fad5b-d9cb-469f-a165-70867728950e\"}"
            })
    void refusesMalformedMessages(String json) {
        assertThatThrownBy(() -> ScanProtocol.readResult(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesOversizedMessagesBeforeParsing() {
        assertThatThrownBy(() -> ScanProtocol.readResult(new byte[ScanProtocol.MAX_MESSAGE_BYTES + 1]))
                .isInstanceOf(ScanProtocol.InvalidMessageException.class);
    }

    @Test
    void anIncompleteScanMustSayWhy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ScanResult(1, JOB, TRIVY, "0.55", "db", ScanResult.Status.INCOMPLETE, null, List.of()));
        assertThat(new ScanResult(
                                1,
                                JOB,
                                TRIVY,
                                "0.55",
                                "db",
                                ScanResult.Status.INCOMPLETE,
                                "max unpacked size",
                                List.of())
                        .incompleteReason())
                .isEqualTo("max unpacked size");
    }

    @Test
    void aFailedScanCarriesNoFindings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScanResult(
                        1,
                        JOB,
                        TRIVY,
                        "0.55",
                        "db",
                        ScanResult.Status.FAILED,
                        null,
                        List.of(cve("CVE-1", "pkg:npm/a@1", Severity.LOW))));
    }

    /** A compromised worker must not be able to inject markup, log lines or unbounded data. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "CVE-1\nINFO forged log line",
                "<script>alert(1)</script>",
                "CVE 2024",
                "../../etc",
                "-rf",
                "CVE-\u0000"
            })
    void refusesHostileIdentifiers(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> cve(id, null, Severity.HIGH));
        assertThatIllegalArgumentException().isThrownBy(() -> cve("CVE-1", null, Severity.HIGH, id));
    }

    @Test
    void boundsEveryField() {
        assertThatIllegalArgumentException().isThrownBy(() -> cve("C".repeat(129), null, Severity.LOW));
        assertThatIllegalArgumentException().isThrownBy(() -> cve("CVE-1", "npm/lodash@4", Severity.LOW));
        assertThatIllegalArgumentException().isThrownBy(() -> cve("CVE-1", "pkg:" + "a".repeat(2048), Severity.LOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Finding(
                        Finding.Kind.SECRET, "aws-key", List.of(), null, Severity.HIGH, null, "t".repeat(513)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Finding(
                        Finding.Kind.SECRET, "aws-key", List.of(), null, Severity.HIGH, null, "line1\nline2"));
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i <= Finding.MAX_ALIASES; i++) {
            aliases.add("A-" + i);
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Finding(Finding.Kind.VULNERABILITY, "CVE-1", aliases, null, Severity.LOW, null, null));
    }

    @Test
    void boundsTheNumberOfFindings() {
        Finding finding = cve("CVE-1", null, Severity.LOW);
        List<Finding> many = Collections.nCopies(ScanResult.MAX_FINDINGS + 1, finding);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScanResult(1, JOB, TRIVY, "0.55", "db", ScanResult.Status.COMPLETE, null, many));
    }

    @Test
    void jobsNeedAScannerAndPositiveLimits() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Limits(0, 1, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Limits(1, 1, -1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScanJob(
                        1,
                        JOB,
                        JOB,
                        Digest.sha256(new byte[0]),
                        "x",
                        URI.create("https://x"),
                        Instant.EPOCH,
                        List.of(),
                        new Limits(1, 1, 1, 1)));
    }

    @Test
    void ranksSeverities() {
        assertThat(Severity.LOW.max(Severity.HIGH)).isEqualTo(Severity.HIGH);
        assertThat(Severity.CRITICAL.max(Severity.UNKNOWN)).isEqualTo(Severity.CRITICAL);
        assertThat(Severity.UNKNOWN.max(Severity.LOW)).isEqualTo(Severity.LOW);
    }

    @FuzzTest
    void readingOnlyFailsWithProtocolErrors(FuzzedDataProvider data) {
        try {
            ScanProtocol.readResult(data.consumeRemainingAsBytes());
        } catch (IllegalArgumentException expected) {
            // Invalid and unsupported messages are both IllegalArgumentExceptions.
        }
    }

    @Test
    void scannerIdsAreOpenAndTravelAsStrings() {
        String json = new String(ScanProtocol.write(result(new ScannerId("guarddog"))), StandardCharsets.UTF_8);
        assertThat(json).contains("\"scanner\":\"guarddog\"");
        assertThat(ScanProtocol.readResult(json.getBytes(StandardCharsets.UTF_8))
                        .scanner())
                .isEqualTo(new ScannerId("guarddog"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "Trivy",
                "1trivy",
                "trivy scanner",
                "trivy\n",
                "../trivy",
                "abcdefghijklmnopqrstuvwxyz0123456"
            })
    void refusesUnsafeScannerIds(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ScannerId(id));
        String json = new String(ScanProtocol.write(result(TRIVY)), StandardCharsets.UTF_8)
                .replace("\"trivy\"", "\"" + id.replace("\n", "\\n") + "\"");
        assertThatThrownBy(() -> ScanProtocol.readResult(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
