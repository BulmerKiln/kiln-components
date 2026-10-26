/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArtifactFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"oci", "npm", "maven", "cargo", "helm-classic", "a"})
    void acceptsFormatIds(String id) {
        assertThat(new ArtifactFormat(id)).hasToString(id);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "OCI", "1oci", "-oci", "o ci", "o/ci", "o.ci", "oci\n", "abcdefghijklmnopqrstuvwxyz0123456"})
    void refusesIdsUnsafeAsLabelsOrColumns(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactFormat(id));
    }

    @Test
    void equalIdsAreTheSameFormat() {
        assertThat(new ArtifactFormat("npm")).isEqualTo(new ArtifactFormat("npm"));
    }
}
