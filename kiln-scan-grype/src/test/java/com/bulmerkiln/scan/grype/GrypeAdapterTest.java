/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.grype;

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
        ScanResult result = ScannerAdapterRunner.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.getFindings()).hasSize(5).allSatisfy(f -> {
            assertThat(f.getId()).startsWith("GHSA-");
            assertThat(f.getAliases()).singleElement().asString().startsWith("CVE-");
            assertThat(f.getPurl()).isEqualTo("pkg:npm/lodash@4.17.20");
        });
        Finding template = result.getFindings().stream()
                .filter(f -> f.getId().equals("GHSA-35jh-r3h4-6jhm"))
                .findFirst()
                .orElseThrow();
        assertThat(template.getAliases()).containsExactly("CVE-2021-23337");
        assertThat(template.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(template.getFixedVersion()).isEqualTo("4.17.21");
    }

    @Test
    void reportsNoFixWhenGrypeHasNone() {
        String output = sampleOutput().replaceFirst("\"state\":\"fixed\"", "\"state\":\"not-fixed\"");
        long withoutFix = ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings().stream()
                .filter(f -> f.getFixedVersion() == null)
                .count();
        assertThat(withoutFix).isEqualTo(1);
    }

    @Test
    void refusesReportsFromAnotherTool() {
        String output = sampleOutput().replaceFirst("\"name\":\"grype\"", "\"name\":\"impostor\"");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getStatus())
                .isEqualTo(ScanResult.Status.FAILED);
    }

    @Test
    void skipsBlankAndSelfReferencingAliases() {
        String output = sampleOutput()
                .replace(
                        "\"relatedVulnerabilities\":[{\"id\":\"CVE-2021-23337\"",
                        "\"relatedVulnerabilities\":[{\"id\":\"\"},{\"id\":\"GHSA-35jh-r3h4-6jhm\"},"
                                + "{\"id\":\"CVE-2021-23337\"");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").getAliases()).containsExactly("CVE-2021-23337");
    }

    @Test
    void takesTheFirstFixedVersionThatIsText() {
        String output = sampleOutput().replace("\"versions\":[\"4.17.21\"]", "\"versions\":[421,\"4.17.21\"]");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").getFixedVersion()).isEqualTo("4.17.21");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[421]", "null"})
    void reportsNoFixWhenNoFixedVersionIsText(String versions) {
        String output = sampleOutput().replace("\"versions\":[\"4.17.21\"]", "\"versions\":" + versions);
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").getFixedVersion()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"purl\":\"pkg:npm/lodash@4.17.20\"", "\"purl\":" + purl);
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.getPurl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output =
                sampleOutput().replace("\"relatedVulnerabilities\":[", "\"relatedVulnerabilities\":null,\"x\":[");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.getAliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output =
                sampleOutput().replace("\"relatedVulnerabilities\":[", "\"relatedVulnerabilities\":\"x\",\"y\":[");
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
