/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SealedValueTest {

    /** Equal values agree on equals and hashCode; a different context or ciphertext does not. */
    @Test
    void equalityIsByContextAndCiphertext() {
        SealedValue a = new SealedValue("1", new byte[] {1, 2, 3});
        SealedValue b = new SealedValue("1", new byte[] {1, 2, 3});
        SealedValue otherContext = new SealedValue("2", new byte[] {1, 2, 3});
        SealedValue otherBytes = new SealedValue("1", new byte[] {1, 2, 4});
        Object notASealedValue = "not-a-sealed-value";

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.equals(otherContext)).isFalse();
        assertThat(a.equals(otherBytes)).isFalse();
        assertThat(a.equals(notASealedValue)).isFalse();
        assertThat(a.equals(null)).isFalse();
    }

    /** The ciphertext is copied on the way in and out, so neither side can mutate the other. */
    @Test
    void copiesTheCiphertext() {
        byte[] bytes = {1, 2, 3};
        SealedValue sealed = new SealedValue("1", bytes);
        bytes[0] = 99;

        assertThat(sealed.getCiphertext()).containsExactly(1, 2, 3);
        sealed.getCiphertext()[0] = 99;
        assertThat(sealed.getCiphertext()).containsExactly(1, 2, 3);
    }
}
