/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.trivy;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerAdapterContract;
import com.bulmerkiln.scan.constant.Severity;
import com.bulmerkiln.scan.domain.Finding;
import com.bulmerkiln.scan.domain.ScanResult;
import com.bulmerkiln.scan.service.ScannerAdapterRunner;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
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
        ScanResult result = ScannerAdapterRunner.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.getFindings()).hasSize(5).allSatisfy(f -> {
            assertThat(f.getKind()).isEqualTo(Finding.Kind.VULNERABILITY);
            assertThat(f.getPurl()).isEqualTo("pkg:npm/lodash@4.17.20");
            assertThat(f.getAliases()).singleElement().asString().startsWith("GHSA-");
        });
        Finding template = result.getFindings().stream()
                .filter(f -> f.getId().equals("CVE-2021-23337"))
                .findFirst()
                .orElseThrow();
        assertThat(template.getAliases()).containsExactly("GHSA-35jh-r3h4-6jhm");
        assertThat(template.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(template.getFixedVersion()).isEqualTo("4.17.21");
        assertThat(template.getTitle()).isEqualTo("nodejs-lodash: command injection via template");
    }

    @Test
    void skipsWhatVexMarksNotAffected() {
        String output = sampleOutput().replaceFirst("\"Status\": \"fixed\"", "\"Status\": \"not_affected\"");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .hasSize(4);
    }

    @Test
    void refusesOtherReportSchemas() {
        String output = sampleOutput().replace("\"SchemaVersion\": 2", "\"SchemaVersion\": 3");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getStatus())
                .isEqualTo(ScanResult.Status.FAILED);
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
        assertThat(find(output, "CVE-2021-23337").getAliases()).containsExactly("GHSA-35jh-r3h4-6jhm");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"PURL\": \"pkg:npm/lodash@4.17.20\"", "\"PURL\": " + purl);
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.getPurl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output = sampleOutput().replace("\"VendorIDs\": [", "\"VendorIDs\": null, \"x\": [");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.getAliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output = sampleOutput().replace("\"VendorIDs\": [", "\"VendorIDs\": \"x\", \"y\": [");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getStatus())
                .isEqualTo(ScanResult.Status.FAILED);
    }

    /** Whatever the tool outputs, parsing fails only with a RuntimeException. */
    @FuzzTest
    void parsingFailsOnlyWithRuntimeException(FuzzedDataProvider data) {
        try {
            adapter().parse(JOB, run(data.consumeRemainingAsString()));
        } catch (RuntimeException expected) {
            // The only failure a crafted tool output may cause.
        }
    }

    private Finding find(String output, String id) {
        return ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings().stream()
                .filter(f -> f.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
