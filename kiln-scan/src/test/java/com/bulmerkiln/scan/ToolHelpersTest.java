/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ToolHelpersTest {

    @ParameterizedTest
    @CsvSource({
        "0.56.2, true",
        "v0.56.2, true",
        "0.58.0, true",
        "0.59.0, false",
        "0.55.9, false",
        "1.0.0, false",
        "0.56.2-rc1, true",
        "0.56, false",
        "' 0.56.2 ', false"
    })
    void zeroXToolsAreBoundedByMinor(String version, boolean supported) {
        assertThat(ToolVersion.supported(version, new ToolVersion(0, 56, 0), 58))
                .isEqualTo(supported);
    }

    @ParameterizedTest
    @CsvSource({"8.21.2, true", "8.99.0, true", "9.0.0, false", "8.20.9, false"})
    void stableToolsAreBoundedByMajor(String version, boolean supported) {
        assertThat(ToolVersion.supported(version, new ToolVersion(8, 21, 0), 0)).isEqualTo(supported);
    }

    @ParameterizedTest
    @CsvSource({
        "CRITICAL, CRITICAL",
        "Critical, CRITICAL",
        "high, HIGH",
        "MODERATE, MEDIUM",
        "Medium, MEDIUM",
        "Negligible, LOW",
        "low, LOW",
        "Unknown, UNKNOWN",
        "bogus, UNKNOWN",
        "'', UNKNOWN"
    })
    void mapsSeverityNames(String name, Severity severity) {
        assertThat(Severities.named(name)).isEqualTo(severity);
    }

    @ParameterizedTest
    @CsvSource({
        "9.8, CRITICAL",
        "9.0, CRITICAL",
        "7.2, HIGH",
        "4.0, MEDIUM",
        "0.1, LOW",
        "0, UNKNOWN",
        "-1, UNKNOWN",
        "11, UNKNOWN",
        "NaN, UNKNOWN"
    })
    void mapsCvssScores(double score, Severity severity) {
        assertThat(Severities.cvss(score)).isEqualTo(severity);
    }

    @Test
    void cleansToolText() {
        assertThat(ToolText.clean("xz: backdoor\nin liblzma\t ", 512)).isEqualTo("xz: backdoor in liblzma");
        assertThat(ToolText.clean("\u0000\n", 512)).isNull();
        assertThat(ToolText.clean(null, 512)).isNull();
        assertThat(ToolText.clean("a".repeat(600), 512)).hasSize(512);
    }

    @Test
    void readsToolJsonStrictly() {
        assertThat(ToolOutput.json("{\"a\":1}").get("a").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> ToolOutput.json("{\"a\":1,\"a\":2}")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ToolOutput.json("")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ToolOutput.json("{} {}")).isInstanceOf(RuntimeException.class);
    }

    @FuzzTest
    void versionParsingNeverThrows(FuzzedDataProvider data) {
        String version = data.consumeRemainingAsString();
        ToolVersion.parse(version)
                .ifPresent(parsed -> assertThat(parsed.major()).isNotNegative());
    }
}
