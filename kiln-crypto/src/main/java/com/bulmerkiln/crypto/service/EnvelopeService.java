/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.service;

import com.bulmerkiln.core.annotation.UntrustedInput;
import com.bulmerkiln.crypto.CryptoException;
import com.bulmerkiln.crypto.KeyProvider;
import com.bulmerkiln.crypto.domain.SealedValue;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsEnvelopeAead;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Seals and opens values with Tink's envelope encryption: a fresh AES-256-GCM data key for every
 * value, wrapped by the KEK (CRYPTO-2). Each value is bound to a context, such as
 * {@code signing-key:<organization>:<kid>}, so ciphertext moved to another row doesn't open
 * (CRYPTO-3).
 */
public final class EnvelopeService {

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Tink's AEAD primitives are unavailable", e);
        }
    }

    private final KeyProvider keys;

    public EnvelopeService(KeyProvider keys) {
        this.keys = keys;
    }

    /** Encrypts {@code plaintext} for {@code context}, under the current KEK. */
    public SealedValue seal(byte[] plaintext, String context) {
        String kekId = keys.kekId();
        try {
            return new SealedValue(kekId, envelope().encrypt(plaintext, associatedData(context)));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("The value couldn't be sealed");
        }
    }

    /**
     * Decrypts a value sealed for {@code context}. A wrong context, a tampered ciphertext or a KEK
     * that no longer holds the key all fail the same way, without saying which (CRYPTO-4).
     */
    @UntrustedInput
    public byte[] open(SealedValue sealed, String context) {
        try {
            return envelope().decrypt(sealed.getCiphertext(), associatedData(context));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new CryptoException("The value couldn't be opened");
        }
    }

    /** Seals the value again under the current KEK, after a rotation (CRYPTO-5). */
    public SealedValue rewrap(SealedValue sealed, String context) {
        if (sealed.getKekId().equals(keys.kekId())) {
            return sealed;
        }
        return seal(open(sealed, context), context);
    }

    private Aead envelope() throws GeneralSecurityException {
        return KmsEnvelopeAead.create(PredefinedAeadParameters.AES256_GCM, keys.kek());
    }

    private static byte[] associatedData(String context) {
        if (context.isBlank()) {
            throw new IllegalArgumentException("A sealed value needs a context");
        }
        return context.getBytes(StandardCharsets.UTF_8);
    }
}
