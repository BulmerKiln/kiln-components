/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.osv;

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
        ScanResult result = ScannerAdapters.run(adapter(), JOB, run(sampleOutput()));
        assertThat(result.findings()).hasSize(5);
        Finding template = result.findings().stream()
                .filter(f -> f.id().equals("GHSA-35jh-r3h4-6jhm"))
                .findFirst()
                .orElseThrow();
        assertThat(template.aliases()).containsExactly("CVE-2021-23337");
        assertThat(template.severity()).as("max_severity 7.2").isEqualTo(Severity.HIGH);
        assertThat(template.fixedVersion()).isEqualTo("4.17.21");
        assertThat(template.purl()).isEqualTo("pkg:npm/lodash@4.17.20");
        assertThat(template.title()).isEqualTo("Command Injection in lodash");
    }

    @Test
    void reportsMaliciousPackagesAsCriticalMalware() {
        String output = sampleOutput().replace("GHSA-35jh-r3h4-6jhm", "MAL-2026-1234");
        Finding malware = ScannerAdapters.run(adapter(), JOB, run(output)).findings().stream()
                .filter(f -> f.id().equals("MAL-2026-1234"))
                .findFirst()
                .orElseThrow();
        assertThat(malware.kind()).isEqualTo(Finding.Kind.MALWARE);
        assertThat(malware.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void findingsAreACompletedScanNotAFailure() {
        assertThat(ScannerAdapters.run(adapter(), JOB, run(sampleOutput())).status())
                .isEqualTo(ScanResult.Status.COMPLETE);
    }

    @Test
    void skipsAliasesThatAreNotTextOrAreTheFindingItself() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": [42, \"GHSA-35jh-r3h4-6jhm\", ");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").aliases()).containsExactly("CVE-2021-23337");
    }

    @ParameterizedTest
    @ValueSource(strings = {"high", "0", ""})
    void fallsBackToTheAdvisorysSeverityWithoutAUsableCvssScore(String score) {
        String output = sampleOutput().replace("\"max_severity\": \"7.2\"", "\"max_severity\": \"" + score + "\"");
        assertThat(find(output, "GHSA-35jh-r3h4-6jhm").severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void reportsNoFixWhenNoRangeHasOne() {
        String output = sampleOutput().replace("\"fixed\": \"", "\"last_affected\": \"");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.fixedVersion()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"npm/lodash@4.17.20\"", "42"})
    void dropsAPurlThatIsNotOne(String purl) {
        String output = sampleOutput().replace("\"purl\": \"pkg:npm/lodash@4.17.20\"", "\"purl\": " + purl);
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.purl()).isNull());
    }

    @Test
    void readsANullListAsEmpty() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": null, \"x\": [");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).findings())
                .hasSize(5)
                .allSatisfy(f -> assertThat(f.aliases()).isEmpty());
    }

    @Test
    void refusesAListThatIsNotOne() {
        String output = sampleOutput().replace("\"aliases\": [", "\"aliases\": \"x\", \"y\": [");
        assertThat(ScannerAdapters.run(adapter(), JOB, run(output)).status()).isEqualTo(ScanResult.Status.FAILED);
    }

    private Finding find(String output, String id) {
        return ScannerAdapters.run(adapter(), JOB, run(output)).findings().stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
