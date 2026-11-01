/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.crypto.CryptoException;
import com.bulmerkiln.crypto.domain.SealedValue;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvelopeServiceTest {

    private static final byte[] SECRET = "a signing key".getBytes(StandardCharsets.UTF_8);
    private static final String CONTEXT = "signing-key:acme:k1";

    @TempDir
    Path dir;

    /** CRYPTO-2: a value opens again, and each seal uses a fresh data key. */
    @Test
    void sealsAndOpens() {
        EnvelopeService envelope = new EnvelopeService(KeyFiles.provider(dir.resolve("kek.json")));

        SealedValue first = envelope.seal(SECRET, CONTEXT);
        SealedValue second = envelope.seal(SECRET, CONTEXT);

        assertThat(envelope.open(first, CONTEXT)).isEqualTo(SECRET);
        assertThat(first.getCiphertext()).isNotEqualTo(second.getCiphertext());
        assertThat(new String(first.getCiphertext(), StandardCharsets.ISO_8859_1))
                .doesNotContain("a signing key");
        assertThat(first.toString()).doesNotContain("signing");
    }

    /** CRYPTO-3, CRYPTO-4: another context, a flipped byte or another KEK all fail alike. */
    @Test
    void opensOnlyWhatItSealedForTheContext() {
        EnvelopeService envelope = new EnvelopeService(KeyFiles.provider(dir.resolve("kek.json")));
        SealedValue sealed = envelope.seal(SECRET, CONTEXT);
        byte[] tampered = sealed.getCiphertext();
        tampered[tampered.length - 1] ^= 1;
        EnvelopeService other = new EnvelopeService(KeyFiles.provider(dir.resolve("other.json")));

        assertThatThrownBy(() -> envelope.open(sealed, "signing-key:globex:k1"))
                .isInstanceOf(CryptoException.class)
                .hasMessage("The value couldn't be opened");
        assertThatThrownBy(() -> envelope.open(new SealedValue(sealed.getKekId(), tampered), CONTEXT))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> other.open(sealed, CONTEXT)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> envelope.seal(SECRET, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    /** CRYPTO-5: after a rotation old values still open, and rewrapping moves them to the new KEK. */
    @Test
    void rewrapsAfterARotation() {
        Path kek = dir.resolve("kek.json");
        FileKeyProvider before = KeyFiles.provider(kek);
        SealedValue sealed = new EnvelopeService(before).seal(SECRET, CONTEXT);

        KeyFiles.rotate(kek);
        FileKeyProvider after = FileKeyProvider.load(kek, false);
        EnvelopeService rotated = new EnvelopeService(after);
        SealedValue rewrapped = rotated.rewrap(sealed, CONTEXT);

        assertThat(after.kekId()).isNotEqualTo(before.kekId());
        assertThat(rotated.open(sealed, CONTEXT)).isEqualTo(SECRET);
        assertThat(rewrapped.getKekId()).isEqualTo(after.kekId());
        assertThat(rotated.open(rewrapped, CONTEXT)).isEqualTo(SECRET);
        assertThat(rotated.rewrap(rewrapped, CONTEXT)).isSameAs(rewrapped);
    }

    /** CRYPTO-4: whatever bytes are stored, opening them fails only with a CryptoException. */
    @FuzzTest
    void openingAnythingFailsOnlyAsACryptoException(FuzzedDataProvider data) {
        EnvelopeService envelope = new EnvelopeService(KeyFiles.provider(dir.resolve("fuzz.json")));
        try {
            envelope.open(new SealedValue("1", data.consumeRemainingAsBytes()), CONTEXT);
        } catch (CryptoException expected) {
            // The only failure stored bytes may cause.
        }
    }
}
