/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FMT-13: strict SemVer 2.0.0. */
class SemanticVersionTest {

    @ParameterizedTest
    @CsvSource({"0.0.0", "1.0.0", "0.0.1-alpha.1", "1.2.3+build.5", "10.20.30-rc.1+meta", "1.0.0-x-y"})
    void acceptsSemVer(String version) {
        assertThat(SemanticVersion.isValid(version)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "01.0.0",
        "1.0",
        "v1.0.0",
        "1.0.0-",
        "1.0.0+",
        "latest",
        "1.0.0-a..b",
        "1.0.0-.a",
        "1.0.0+b.",
        "1.0.0-a_b",
        "1.0.0+x+y",
        "1.0.0-é",
        "''"
    })
    void refusesEverythingElse(String version) {
        assertThat(SemanticVersion.isValid(version)).isFalse();
    }

    @Test
    void refusesOverlongVersions() {
        assertThat(SemanticVersion.isValid("1.0.0-" + "a".repeat(251))).isFalse();
        assertThat(SemanticVersion.isValid("1.0.0-" + "a".repeat(250))).isTrue();
    }

    /** Whatever string a URL or manifest carries, validation never throws. */
    @FuzzTest
    void versionValidationNeverThrows(FuzzedDataProvider data) {
        SemanticVersion.isValid(data.consumeString(512));
    }
}
