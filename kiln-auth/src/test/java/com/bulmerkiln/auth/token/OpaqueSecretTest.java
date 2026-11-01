/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpaqueSecretTest {

    /** AUTH-21: a secret is 256 random bits as 43 base64url characters, and parses back. */
    @Test
    void roundTrips() {
        OpaqueSecret secret = OpaqueSecret.generate();

        assertThat(secret.reveal()).matches("[A-Za-z0-9_-]{43}");
        assertThat(OpaqueSecret.parse(secret.reveal()).orElseThrow().sha256()).isEqualTo(secret.sha256());
    }

    /** AUTH-21: secrets don't repeat. */
    @Test
    void secretsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(OpaqueSecret.generate().reveal())).isTrue();
        }
    }

    /** AUTH-21: anything but the exact shape is refused before a lookup. */
    @Test
    void parseRefusesOtherShapes() {
        for (String presented :
                new String[] {"", "A".repeat(42), "A".repeat(44), "A".repeat(42) + "=", "A".repeat(42) + "+"}) {
            assertThat(OpaqueSecret.parse(presented)).as(presented).isEmpty();
        }
    }

    /** AUTH-21: the string form never reveals the secret. */
    @Test
    void toStringIsRedacted() {
        OpaqueSecret secret = OpaqueSecret.generate();

        assertThat(secret.toString()).doesNotContain(secret.reveal());
    }

    @FuzzTest
    void parseNeverThrows(FuzzedDataProvider data) {
        OpaqueSecret.parse(data.consumeRemainingAsString());
    }
}
