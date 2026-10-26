/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.gitleaks;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanProtocol;
import com.bulmerkiln.scan.ScanResult;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerAdapterContract;
import com.bulmerkiln.scan.ScannerAdapters;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GitleaksAdapterTest extends ScannerAdapterContract {

    @Override
    protected ScannerAdapter adapter() {
        return new GitleaksAdapter();
    }

    @Override
    protected String sampleToolVersion() {
        return "v8.30.1";
    }

    @Override
    protected int sampleExitCode() {
        return 1;
    }

    @Override
    protected String sampleOutput() {
        return resource("gitleaks-8.30.1-redacted.json");
    }

    @Override
    protected String emptyOutput() {
        return resource("gitleaks-8.30.1-clean.json");
    }

    @Override
    protected String outputWithSecret(String secret) {
        return resource("gitleaks-8.30.1-unredacted.json").replace("%SECRET%", secret);
    }

    @Test
    void reportsTheRuleNotTheLeak() {
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(outputWithSecret(PLANTED_SECRET)));
        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(Finding.Kind.SECRET);
            assertThat(f.id()).isEqualTo("aws-access-token");
            assertThat(f.title()).startsWith("Identified a pattern that may indicate AWS credentials");
        });
        String json = new String(ScanProtocol.write(result), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain(PLANTED_SECRET).doesNotContain("config.env");
    }

    @Test
    void alwaysAsksGitleaksToRedact() {
        assertThat(adapter().command(JOB, Path.of("/workspace/job/content"))).contains("--redact");
    }
}
