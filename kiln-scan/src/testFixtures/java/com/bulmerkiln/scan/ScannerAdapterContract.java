/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.digest.Digest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What every {@link ScannerAdapter} must do. An adapter's test extends this with output recorded
 * from the real tool, kept as test resources, so the suite never needs the tool or Docker.
 */
public abstract class ScannerAdapterContract {

    protected static final ScanJob JOB = new ScanJob(
            ScanProtocol.VERSION,
            UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"),
            UUID.fromString("7d6f5a2e-1c3b-4a5d-9e8f-0a1b2c3d4e5f"),
            Digest.sha256(new byte[0]),
            "application/vnd.oci.image.manifest.v1+json",
            URI.create("https://pkg.example/blobs/x?X-Amz-Signature=PRESIGNED-SECRET"),
            Instant.parse("2026-10-26T12:00:00Z"),
            List.of(new ScannerId("example")),
            new Limits(1L << 30, 10_000, 16, 600));

    protected static final String PLANTED_SECRET = "AKIAPLANTEDSECRET1234";

    protected abstract ScannerAdapter adapter();

    /** The tool version the recordings came from. */
    protected abstract String sampleToolVersion();

    /** Recorded output from the real tool with at least one finding. */
    protected abstract String sampleOutput();

    /** Recorded output from the real tool for a clean scan, with nothing found. */
    protected abstract String emptyOutput();

    /**
     * Recorded output in which the tool reported {@code secret}, for scanners that find secrets;
     * null for the others.
     */
    protected abstract String outputWithSecret(String secret);

    /** The exit code the tool gives with the sample output. */
    protected int sampleExitCode() {
        return 0;
    }

    protected ToolRun run(String output) {
        return new ToolRun(sampleToolVersion(), "db-2026-10-26", sampleExitCode(), output, false, null);
    }

    /** Reads a recording from the test's resources, next to the test class. */
    protected String resource(String name) {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing recording " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void parsesARealReportCompletely() {
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.status()).isEqualTo(ScanResult.Status.COMPLETE);
        assertThat(result.scanner()).isEqualTo(adapter().scanner());
        assertThat(result.jobId()).isEqualTo(JOB.jobId());
        assertThat(result.findings()).isNotEmpty();
    }

    @Test
    void recognisesACleanScan() {
        ScanResult result = ScannerAdapters.run(
                adapter(), JOB, new ToolRun(sampleToolVersion(), "db", 0, emptyOutput(), false, null));
        assertThat(result.status()).isEqualTo(ScanResult.Status.COMPLETE);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void supportsTheRecordedToolVersionAndNothingUnparseable() {
        assertThat(adapter().supportsVersion(sampleToolVersion())).isTrue();
        for (String version : List.of("", "garbage", "latest", "1", "1.2", "1.2.3\n", "99999999.0.0", "999.0.0")) {
            assertThat(adapter().supportsVersion(version)).as(version).isFalse();
        }
    }

    @Test
    void isDeterministic() {
        assertThat(ScannerAdapters.run(adapter(), JOB, run(sampleOutput())))
                .isEqualTo(ScannerAdapters.run(adapter(), JOB, run(sampleOutput())));
    }

    @Test
    void resultsSurviveTheProtocol() {
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sampleOutput()));
        assertThat(ScanProtocol.readResult(ScanProtocol.write(result))).isEqualTo(result);
    }

    /** Hostile or broken output fails the scan; it never throws and never passes. */
    @Test
    void failsOnOutputItCannotRead() {
        List<String> broken = List.of(
                "",
                " ",
                "{",
                "null",
                "true",
                "42",
                "\u0000\u0001binary",
                "<html>502 Bad Gateway</html>",
                "[".repeat(100_000),
                "{\"unexpected\":\"shape\"}",
                "x".repeat(10_000_000));
        for (String output : broken) {
            ScanResult result = ScannerAdapters.run(adapter(), JOB, run(output));
            assertThat(result.status())
                    .as(output.length() > 40 ? output.substring(0, 40) : output)
                    .isNotEqualTo(ScanResult.Status.COMPLETE);
        }
    }

    @Test
    void aTruncatedReportIsNeverClean() {
        String sample = sampleOutput();
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sample.substring(0, sample.length() / 2)));
        assertThat(result.status()).isNotEqualTo(ScanResult.Status.COMPLETE);
    }

    @Test
    void aToolThatDidNotCompleteFails() {
        ToolRun crashed = new ToolRun(sampleToolVersion(), "db", 137, sampleOutput(), false, null);
        ScanResult result = ScannerAdapters.run(adapter(), JOB, crashed);
        assertThat(result.status()).isEqualTo(ScanResult.Status.FAILED);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void limitsAndTimeoutsAreIncompleteNotClean() {
        ScanResult limited = ScannerAdapters.run(
                adapter(), JOB, new ToolRun(sampleToolVersion(), "db", 2, sampleOutput(), false, "max files"));
        assertThat(limited.status()).isEqualTo(ScanResult.Status.INCOMPLETE);
        assertThat(limited.incompleteReason()).isEqualTo("max files");
        ScanResult timedOut = ScannerAdapters.run(
                adapter(), JOB, new ToolRun(sampleToolVersion(), "db", 143, sampleOutput(), true, null));
        assertThat(timedOut.status()).isEqualTo(ScanResult.Status.INCOMPLETE);
    }

    @Test
    void hostileToolVersionsCannotBreakTheResult() {
        ToolRun run = new ToolRun(
                sampleToolVersion() + "\nFORGED LOG LINE" + "v".repeat(500),
                "\u0000",
                sampleExitCode(),
                sampleOutput(),
                false,
                null);
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run);
        assertThat(result.scannerVersion()).doesNotContain("\n").hasSizeLessThanOrEqualTo(128);
        assertThat(result.databaseVersion()).isEqualTo("unknown");
    }

    @Test
    void neverReportsASecretsValue() {
        String output = outputWithSecret(PLANTED_SECRET);
        if (output == null) {
            return;
        }
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(output));
        assertThat(result.status()).isEqualTo(ScanResult.Status.COMPLETE);
        assertThat(result.findings()).isNotEmpty();
        assertThat(new String(ScanProtocol.write(result), StandardCharsets.UTF_8))
                .doesNotContain(PLANTED_SECRET);
    }

    @Test
    void runsTheToolDirectlyOnTheLocalCopy() {
        Path input = Path.of("/workspace/job/artifact");
        List<String> command = adapter().command(JOB, input);
        assertThat(command).isNotEmpty();
        assertThat(command.getFirst()).isNotIn("sh", "bash", "/bin/sh", "/bin/bash", "cmd", "powershell");
        assertThat(command).doesNotContain("-c").anyMatch(arg -> arg.contains(input.toString()));
        assertThat(String.join(" ", command))
                .doesNotContain("PRESIGNED-SECRET")
                .doesNotContain(JOB.blobUrl().toString());
        assertThat(command).noneMatch(arg -> arg.indexOf('\0') >= 0);
    }
}
