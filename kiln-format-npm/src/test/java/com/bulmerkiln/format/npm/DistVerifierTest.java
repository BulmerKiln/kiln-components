/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DistVerifierTest {

    private static final byte[] TARBALL = "package/package.json".getBytes(StandardCharsets.UTF_8);
    private static final String INTEGRITY = "sha512-" + Base64.getEncoder().encodeToString(hash("SHA-512", TARBALL));
    private static final String SHASUM = HexFormat.of().formatHex(hash("SHA-1", TARBALL));

    @Test
    void acceptsMatchingHashes() {
        assertThatCode(() -> verifier(TARBALL).verify(INTEGRITY, SHASUM)).doesNotThrowAnyException();
        assertThatCode(() -> verifier(TARBALL).verify(INTEGRITY, null)).doesNotThrowAnyException();
    }

    @Test
    void hashesChunkedInput() {
        DistVerifier verifier = new DistVerifier();
        for (int i = 0; i < TARBALL.length; i++) {
            verifier.update(TARBALL, i, 1);
        }
        assertThatCode(() -> verifier.verify(INTEGRITY, SHASUM)).doesNotThrowAnyException();
    }

    @Test
    void acceptsAnyListedSha512AndIgnoresWeakerAlgorithms() {
        String other = "sha512-" + Base64.getEncoder().encodeToString(new byte[64]);
        assertThatCode(() -> verifier(TARBALL).verify("sha1-abc " + other + "  " + INTEGRITY + "?opt", SHASUM))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesTamperedContent() {
        byte[] tampered = TARBALL.clone();
        tampered[0] ^= 1;
        assertThatThrownBy(() -> verifier(tampered).verify(INTEGRITY, null)).hasMessageContaining("integrity");
    }

    @Test
    void refusesAMismatchedShasumEvenWhenIntegrityMatches() {
        assertThatThrownBy(() -> verifier(TARBALL).verify(INTEGRITY, "0".repeat(40)))
                .hasMessageContaining("shasum");
        assertThatThrownBy(() -> verifier(TARBALL).verify(INTEGRITY, SHASUM.toUpperCase(Locale.ROOT)))
                .hasMessageContaining("shasum");
    }

    /** Downgrades and malformed values must never count as a match. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "sha1-AAAA",
                "sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=",
                "sha512-",
                "sha512-!!!",
                "sha512-AAAA",
                "SHA512-abc",
                "sha512 -abc"
            })
    void refusesWeakOrMalformedIntegrity(String integrity) {
        assertThatThrownBy(() -> verifier(TARBALL).verify(integrity, SHASUM)).isInstanceOf(NpmException.class);
    }

    @Test
    void refusesATruncatedOrExtendedHash() {
        byte[] digest = hash("SHA-512", TARBALL);
        String truncated = "sha512-" + Base64.getEncoder().encodeToString(Arrays.copyOf(digest, 63));
        String extended = "sha512-" + Base64.getEncoder().encodeToString(Arrays.copyOf(digest, 65));
        assertThatThrownBy(() -> verifier(TARBALL).verify(truncated, null)).isInstanceOf(NpmException.class);
        assertThatThrownBy(() -> verifier(TARBALL).verify(extended, null)).isInstanceOf(NpmException.class);
    }

    private static DistVerifier verifier(byte[] content) {
        DistVerifier verifier = new DistVerifier();
        verifier.update(content, 0, content.length);
        return verifier;
    }

    private static byte[] hash(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
