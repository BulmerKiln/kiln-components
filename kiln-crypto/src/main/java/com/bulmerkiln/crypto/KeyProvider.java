/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto;

import com.google.crypto.tink.Aead;

/**
 * Holds the key-encryption key (KEK). It only ever wraps and unwraps data keys; a provider backed
 * by a KMS never lets the KEK leave it (CRYPTO-1).
 */
public interface KeyProvider {

    /** The KEK, as an {@link Aead} that wraps with the current key and unwraps with any it holds. */
    Aead kek();

    /** The current KEK's ID, recorded with each sealed value so a rotation knows what to rewrap. */
    String kekId();
}
