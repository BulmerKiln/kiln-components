/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DigestTest {

    /** The SHA-256 of the empty string, a well-known test vector. */
    private static final String EMPTY_SHA256 =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void computesSha256() {
        assertThat(Digest.sha256(new byte[0])).hasToString(EMPTY_SHA256);
    }

    @Test
    void parsesRegisteredAlgorithms() {
        Digest digest = Digest.parse(EMPTY_SHA256);
        assertThat(digest.algorithm()).isEqualTo(DigestAlgorithm.SHA256);
        assertThat(Digest.parse("sha512:" + "a".repeat(128)).algorithm()).isEqualTo(DigestAlgorithm.SHA512);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "sha256",
                "sha256:",
                ":abc",
                "SHA256:" + "a",
                "sha256:" + "A",
                "sha256:abc",
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85",
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855a",
                "sha256:E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
                "sha256:../../../../etc/passwd",
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85/"
            })
    void rejectsMalformedDigests(String value) {
        assertThatThrownBy(() -> Digest.parse(value)).isInstanceOf(InvalidDigestException.class);
    }

    @Test
    void reportsUnregisteredAlgorithmsAsUnsupported() {
        assertThatThrownBy(() -> Digest.parse("md5:d41d8cd98f00b204e9800998ecf8427e"))
                .isInstanceOf(UnsupportedDigestException.class);
        assertThatThrownBy(() -> Digest.parse("multihash+base58:QmRZxt2b1FVZPNqd8hsiykDL3TdBDeTSPX9Kv46HmX4Gx8"))
                .isInstanceOf(UnsupportedDigestException.class);
    }

    @Property
    void roundTrips(@ForAll byte[] content) {
        Digest digest = Digest.sha256(content);
        assertThat(Digest.parse(digest.toString())).isEqualTo(digest);
    }

    @FuzzTest
    void parseOnlyThrowsInvalidDigest(FuzzedDataProvider data) {
        String value = data.consumeRemainingAsString();
        try {
            Digest digest = Digest.parse(value);
            assertThat(digest.toString()).isEqualTo(value);
        } catch (InvalidDigestException expected) {
            // The only failure parse may report.
        }
    }

    @Test
    void streamsContent() throws Exception {
        Digester digester = Digester.of(DigestAlgorithm.SHA256);
        digester.update(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)));
        assertThat(digester.digest()).isEqualTo(Digest.sha256("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void theConstructorValidatesTooSoNoInvalidDigestCanExist() {
        assertThatThrownBy(() -> new Digest(DigestAlgorithm.SHA256, "abc")).isInstanceOf(InvalidDigestException.class);
        assertThatThrownBy(() -> new Digest(DigestAlgorithm.SHA512, "a".repeat(64)))
                .isInstanceOf(InvalidDigestException.class);
        assertThatThrownBy(() -> new Digest(DigestAlgorithm.SHA256, "../" + "a".repeat(61)))
                .isInstanceOf(InvalidDigestException.class);
        assertThatNullPointerException().isThrownBy(() -> new Digest(null, "a".repeat(64)));
    }

    @Test
    void sha256EncodingIsNotAcceptedForSha512() {
        assertThatThrownBy(() -> Digest.parse("sha512:" + "a".repeat(64))).isInstanceOf(InvalidDigestException.class);
    }

    /** Characters that look like hex digits, or that a path or header parser might treat specially. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "\u0661", // Arabic-Indic one
                "\uFF41", // fullwidth a
                "\u0430", // Cyrillic a
                "\u0000", "\n", "\r\n", " ", "/", "%"
            })
    void rejectsLookalikeAndControlCharacters(String injected) {
        String valid = EMPTY_SHA256;
        assertThatThrownBy(() -> Digest.parse(valid.substring(0, 20) + injected + valid.substring(21)))
                .isInstanceOf(InvalidDigestException.class);
        assertThatThrownBy(() -> Digest.parse(valid + injected)).isInstanceOf(InvalidDigestException.class);
        assertThatThrownBy(() -> Digest.parse(injected + valid)).isInstanceOf(InvalidDigestException.class);
    }

    @Test
    void rejectsHugeInputsQuickly() {
        String huge = "sha256:" + "a".repeat(10_000_000);
        String backtracking = "a.".repeat(50_000) + "!";
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThatThrownBy(() -> Digest.parse(huge)).isInstanceOf(InvalidDigestException.class);
            assertThatThrownBy(() -> Digest.parse(backtracking)).isInstanceOf(InvalidDigestException.class);
        });
    }

    @Test
    void equalDigestsAreInterchangeable() {
        Digest a = Digest.parse(EMPTY_SHA256);
        Digest b = Digest.sha256(new byte[0]);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(Digest.sha256(new byte[1]));
    }
}
