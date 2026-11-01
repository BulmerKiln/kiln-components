/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.domain;

import java.util.Arrays;
import java.util.Objects;
import lombok.Value;

/**
 * A value sealed under a data key, which is itself wrapped by the KEK, in one ciphertext.
 */
@Value
public final class SealedValue {

    /** The KEK that wrapped the data key, so a rotation can find what to rewrap. */
    private final String kekId;

    /** The wrapped data key and the encrypted value, as Tink's envelope lays them out. */
    private final byte[] ciphertext;

    public SealedValue(String kekId, byte[] ciphertext) {
        Objects.requireNonNull(kekId, "kekId");
        this.ciphertext = ciphertext.clone();
        this.kekId = kekId;
    }

    public byte[] getCiphertext() {
        return ciphertext.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SealedValue that
                && kekId.equals(that.kekId)
                && Arrays.equals(ciphertext, that.ciphertext);
    }

    @Override
    public int hashCode() {
        return 31 * kekId.hashCode() + Arrays.hashCode(ciphertext);
    }

    @Override
    public String toString() {
        return "SealedValue[kekId=" + kekId + ", " + ciphertext.length + " bytes]";
    }
}
