/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.grype;

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

class GrypeAdapterTest extends ScannerAdapterContract {

    @Override
    protected ScannerAdapter adapter() {
        return new GrypeAdapter();
    }

    @Override
    protected String sampleToolVersion() {
        return "0.121.0";
    }

    @Override
    protected String sampleOutput() {
        return resource("grype-0.121.0-vulnerable.json");
    }

    @Override
    protected String emptyOutput() {
        return resource("grype-0.121.0-clean.json");
    }

    @Override
    protected String outputWithSecret(String secret) {
        return null;
    }

    @Test
    void readsLodashsAdvisoriesWithTheirCveAliases() {
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.findings()).hasSize(5).allSatisfy(f -> {
            assertThat(f.id()).startsWith("GHSA-");
            assertThat(f.aliases()).singleElement().asString().startsWith("CVE-");
            assertThat(f.purl()).isEqualTo("pkg:npm/lodash@4.17.20");
        });
        Finding template = result.findings().stream()
                .filter(f -> f.id().equals("GHSA-35jh-r3h4-6jhm"))
                .findFirst()
                .orElseThrow();
        assertThat(template.aliases()).containsExactly("CVE-2021-23337");
        assertThat(template.severity()).isEqualTo(Severity.HIGH);
        assertThat(template.fixedVersion()).isEqualTo("4.17.21");
    }

    @Test
    void reportsNoFixWhenGrypeHasNone() {
        String output = sampleOutput().replaceFirst("\"state\":\"fixed\"", "\"state\":\"not-fixed\"");
        long withoutFix = ScannerAdapters.run(adapter(), JOB, run(output)).findings().stream()
                .filter(f -> f.fixedVersion() == null)
                .count();
        assertThat(withoutFix).isEqualTo(1);
    }

    @Test
    void refusesReportsFromAnotherTool() {
        String output = sampleOutput().replaceFirst("\"name\":\"grype\"", "\"name\":\"impostor\"");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).status()).isEqualTo(ScanResult.Status.FAILED);
    }

    @Test
    void skipsBlankAndSelfReferencingAliases() {
        String output = sampleOutput()
                .replace(
                        "\"relatedVulnerabilities\":[{\"id\":\"CVE-2021-23337\"",
                        "\"relatedVulnerabilities\":[{\"id\":\"\"},{\"id\":\"GHSA-35jh-r3h4-6jhm\"},"
                                + "{\"id\":\"CVE-2021-23337\"");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").aliases()).containsExactly("CVE-2021-23337");
    }

    @Test
    void takesTheFirstFixedVersionThatIsText() {
        String output = sampleOutput().replace("\"versions\":[\"4.17.21\"]", "\"versions\":[421,\"4.17.21\"]");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").fixedVersion()).isEqualTo("4.17.21");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[421]", "null"})
    void reportsNoFixWhenNoFixedVersionIsText(String versions) {
        String output = sampleOutput().replace("\"versions\":[\"4.17.21\"]", "\"versions\":" + versions);
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").fixedVersion()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"purl\":\"pkg:npm/lodash@4.17.20\"", "\"purl\":" + purl);
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.purl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output =
                sampleOutput().replace("\"relatedVulnerabilities\":[", "\"relatedVulnerabilities\":null,\"x\":[");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.aliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output =
                sampleOutput().replace("\"relatedVulnerabilities\":[", "\"relatedVulnerabilities\":\"x\",\"y\":[");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).status()).isEqualTo(ScanResult.Status.FAILED);
    }

    private Finding find(String output, String id) {
        return ScannerAdapters.run(adapter(), JOB, run(output)).findings().stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
