/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.trivy;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanResult;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerAdapterContract;
import com.bulmerkiln.scan.ScannerAdapters;
import com.bulmerkiln.scan.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrivyAdapterTest extends ScannerAdapterContract {

    @Override
    protected ScannerAdapter adapter() {
        return new TrivyAdapter();
    }

    @Override
    protected String sampleToolVersion() {
        return "0.76.1";
    }

    @Override
    protected String sampleOutput() {
        return resource("trivy-0.76.1-vulnerable.json");
    }

    @Override
    protected String emptyOutput() {
        return resource("trivy-0.76.1-clean.json");
    }

    @Override
    protected String outputWithSecret(String secret) {
        return null;
    }

    @Test
    void readsLodashsVulnerabilitiesWithTheirGhsaAliases() {
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.findings()).hasSize(5).allSatisfy(f -> {
            assertThat(f.kind()).isEqualTo(Finding.Kind.VULNERABILITY);
            assertThat(f.purl()).isEqualTo("pkg:npm/lodash@4.17.20");
            assertThat(f.aliases()).singleElement().asString().startsWith("GHSA-");
        });
        Finding template = result.findings().stream()
                .filter(f -> f.id().equals("CVE-2021-23337"))
                .findFirst()
                .orElseThrow();
        assertThat(template.aliases()).containsExactly("GHSA-35jh-r3h4-6jhm");
        assertThat(template.severity()).isEqualTo(Severity.HIGH);
        assertThat(template.fixedVersion()).isEqualTo("4.17.21");
        assertThat(template.title()).isEqualTo("nodejs-lodash: command injection via template");
    }

    @Test
    void skipsWhatVexMarksNotAffected() {
        String output = sampleOutput().replaceFirst("\"Status\": \"fixed\"", "\"Status\": \"not_affected\"");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings()).hasSize(4);
    }

    @Test
    void refusesOtherReportSchemas() {
        String output = sampleOutput().replace("\"SchemaVersion\": 2", "\"SchemaVersion\": 3");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).status()).isEqualTo(ScanResult.Status.FAILED);
    }

    @Test
    void supportsOnlyRecordedMinorVersions() {
        assertThat(adapter().supportsVersion("0.76.9")).isTrue();
        assertThat(adapter().supportsVersion("0.77.0")).isFalse();
        assertThat(adapter().supportsVersion("0.75.0")).isFalse();
    }

    @Test
    void skipsVendorIdsThatAreNotTextOrAreTheFindingItself() {
        String output = sampleOutput().replace("\"VendorIDs\": [", "\"VendorIDs\": [42, \"CVE-2021-23337\", ");
        assertThat(find(output, "CVE-2021-23337").aliases()).containsExactly("GHSA-35jh-r3h4-6jhm");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"PURL\": \"pkg:npm/lodash@4.17.20\"", "\"PURL\": " + purl);
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.purl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output = sampleOutput().replace("\"VendorIDs\": [", "\"VendorIDs\": null, \"x\": [");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.aliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output = sampleOutput().replace("\"VendorIDs\": [", "\"VendorIDs\": \"x\", \"y\": [");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).status()).isEqualTo(ScanResult.Status.FAILED);
    }

    private Finding find(String output, String id) {
        return ScannerAdapters.run(adapter(), JOB, run(output)).findings().stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
