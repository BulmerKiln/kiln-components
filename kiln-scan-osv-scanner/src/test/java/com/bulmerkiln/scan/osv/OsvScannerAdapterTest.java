/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.osv;

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

class OsvScannerAdapterTest extends ScannerAdapterContract {

    @Override
    protected ScannerAdapter adapter() {
        return new OsvScannerAdapter();
    }

    @Override
    protected String sampleToolVersion() {
        return "2.7.0";
    }

    @Override
    protected int sampleExitCode() {
        return 1;
    }

    @Override
    protected String sampleOutput() {
        return resource("osv-scanner-2.7.0-vulnerable.json");
    }

    @Override
    protected String emptyOutput() {
        return resource("osv-scanner-2.7.0-clean.json");
    }

    @Override
    protected String outputWithSecret(String secret) {
        return null;
    }

    @Test
    void readsSeverityFromTheGroupsCvssScore() {
        ScanResult result = ScannerAdapterRunner.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.getFindings()).hasSize(5);
        Finding template = result.getFindings().stream()
                .filter(f -> f.getId().equals("GHSA-35jh-r3h4-6jhm"))
                .findFirst()
                .orElseThrow();
        assertThat(template.getAliases()).containsExactly("CVE-2021-23337");
        assertThat(template.getSeverity()).as("max_severity 7.2").isEqualTo(Severity.HIGH);
        assertThat(template.getFixedVersion()).isEqualTo("4.17.21");
        assertThat(template.getPurl()).isEqualTo("pkg:npm/lodash@4.17.20");
        assertThat(template.getTitle()).isEqualTo("Command Injection in lodash");
    }

    @Test
    void reportsMaliciousPackagesAsCriticalMalware() {
        String output = sampleOutput().replace("GHSA-35jh-r3h4-6jhm", "MAL-2026-1234");
        Finding malware = ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings().stream()
                .filter(f -> f.getId().equals("MAL-2026-1234"))
                .findFirst()
                .orElseThrow();
        assertThat(malware.getKind()).isEqualTo(Finding.Kind.MALWARE);
        assertThat(malware.getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void findingsAreACompletedScanNotAFailure() {
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(sampleOutput())).getStatus())
                .isEqualTo(ScanResult.Status.COMPLETE);
    }

    @Test
    void skipsAliasesThatAreNotTextOrAreTheFindingItself() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": [42, \"GHSA-35jh-r3h4-6jhm\", ");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").getAliases()).containsExactly("CVE-2021-23337");
    }

    @ParameterizedTest
    @ValueSource(strings = {"high", "0", ""})
    void fallsBackToTheAdvisorysSeverityWithoutAUsableCvssScore(String score) {
        String output = sampleOutput().replace("\"max_severity\": \"7.2\"", "\"max_severity\": \"" + score + "\"");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void reportsNoFixWhenNoRangeHasOne() {
        String output = sampleOutput().replace("\"fixed\": \"", "\"last_affected\": \"");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.getFixedVersion()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"purl\": \"pkg:npm/lodash@4.17.20\"", "\"purl\": " + purl);
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.getPurl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": null, \"x\": [");
        assertThat(ScannerAdapterRunner.run(adapter(), JOB, run(output)).getFindings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.getAliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": \"x\", \"y\": [");
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
