/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Runs the contract against a small adapter, so the suite itself is tested here. */
class ExampleAdapterContractTest extends ScannerAdapterContract {

    /** Reads {@code {"results": [{"id", "severity", "purl", "match"}]}}, never copying "match". */
    static class ExampleAdapter implements ScannerAdapter {

        @Override
        public ScannerId scanner() {
            return new ScannerId("example");
        }

        @Override
        public boolean supportsVersion(String toolVersion) {
            return ToolVersion.supported(toolVersion, new ToolVersion(1, 0, 0), 0);
        }

        @Override
        public List<String> command(ScanJob job, Path input) {
            return List.of("/usr/local/bin/example", "scan", "--format", "json", input.toString());
        }

        @Override
        public List<Finding> parse(ScanJob job, ToolRun run) {
            JsonNode results = ToolOutput.json(run.output()).required("results");
            if (!results.isArray()) {
                throw new IllegalArgumentException("results is not a list");
            }
            List<Finding> findings = new ArrayList<>();
            for (JsonNode node : results) {
                String purl = node.path("purl").isString() ? node.path("purl").asString() : null;
                Finding.Kind kind = purl == null ? Finding.Kind.SECRET : Finding.Kind.VULNERABILITY;
                findings.add(new Finding(
                        kind,
                        node.required("id").asString(),
                        List.of(),
                        purl,
                        Severities.named(node.required("severity").asString()),
                        null,
                        null));
            }
            return findings;
        }
    }

    @Override
    protected ScannerAdapter adapter() {
        return new ExampleAdapter();
    }

    @Override
    protected String sampleToolVersion() {
        return "1.4.2";
    }

    @Override
    protected String sampleOutput() {
        return "{\"results\":[{\"id\":\"CVE-2024-3094\",\"severity\":\"CRITICAL\","
                + "\"purl\":\"pkg:deb/debian/xz-utils@5.6.0\"},"
                + "{\"id\":\"GHSA-xxxx-yyyy-zzzz\",\"severity\":\"LOW\",\"purl\":\"pkg:npm/a@1.0.0\"}]}";
    }

    @Override
    protected String emptyOutput() {
        return "{\"results\":[]}";
    }

    @Override
    protected String outputWithSecret(String secret) {
        return "{\"results\":[{\"id\":\"aws-access-key\",\"severity\":\"HIGH\",\"match\":\"" + secret + "\"}]}";
    }

    /** Shows the secret check has teeth: an adapter that copies the match into a title is detectable. */
    @Test
    void anAdapterThatLeaksASecretIsCaught() {
        ScannerAdapter copying = new ExampleAdapter() {
            @Override
            public List<Finding> parse(ScanJob job, ToolRun run) {
                return List.of(new Finding(
                        Finding.Kind.SECRET,
                        "aws-access-key",
                        List.of(),
                        null,
                        Severity.HIGH,
                        null,
                        "Found " + PLANTED_SECRET));
            }
        };
        ScanResult result = ScannerAdapters.run(copying, JOB, run(outputWithSecret(PLANTED_SECRET)));
        assertThat(new String(ScanProtocol.write(result), StandardCharsets.UTF_8))
                .contains(PLANTED_SECRET);
    }

    @Test
    void tooManyFindingsFailRatherThanTruncate() {
        ScannerAdapter flood = new ExampleAdapter() {
            @Override
            public List<Finding> parse(ScanJob job, ToolRun run) {
                Finding f = new Finding(Finding.Kind.VULNERABILITY, "CVE-1", List.of(), null, Severity.LOW, null, null);
                return Collections.nCopies(ScanResult.MAX_FINDINGS + 1, f);
            }
        };
        assertThat(ScannerAdapters.run(flood, JOB, run(sampleOutput())).status())
                .isEqualTo(ScanResult.Status.FAILED);
    }

    @Test
    void adaptersMayAcceptOtherExitCodes() {
        ScannerAdapter grype = new ExampleAdapter() {
            @Override
            public boolean completed(int exitCode) {
                return exitCode == 0 || exitCode == 1;
            }
        };
        ToolRun foundSomething = new ToolRun("1.4.2", "db", 1, sampleOutput(), false, null);
        assertThat(ScannerAdapters.run(grype, JOB, foundSomething).status()).isEqualTo(ScanResult.Status.COMPLETE);
    }
}
