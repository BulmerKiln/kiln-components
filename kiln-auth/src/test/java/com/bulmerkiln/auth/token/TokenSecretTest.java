/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenSecretTest {

    /** Built independently of the implementation, so a change to the format fails this test. */
    private static final String GOLDEN = "bkp_KilnGoldenToken0123456789abcde4Evm0Z";

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @ParameterizedTest
    @EnumSource(TokenKind.class)
    void generatesTokensThatParseBack(TokenKind kind) {
        TokenSecret token = TokenSecret.generate(kind);

        assertThat(token.reveal()).startsWith(kind.prefix()).hasSize(40).matches(TokenSecret.PATTERN);
        TokenSecret parsed = TokenSecret.parse(token.reveal()).orElseThrow();
        assertThat(parsed.kind()).isEqualTo(kind);
        assertThat(parsed.matches(token.sha256())).isTrue();
    }

    @Test
    void neverRevealsTheSecretInToString() {
        TokenSecret token = TokenSecret.generate(TokenKind.PERSONAL);
        assertThat(token.toString())
                .isEqualTo("bkp_<redacted>")
                .doesNotContain(token.reveal().substring(4));
    }

    @Test
    void rejectsBadChecksumsAndShapes() {
        String token = TokenSecret.generate(TokenKind.SERVICE).reveal();
        char last = token.charAt(token.length() - 1);
        String typo = token.substring(0, token.length() - 1) + (last == '0' ? '1' : '0');

        assertThat(TokenSecret.parse(typo)).isEmpty();
        assertThat(TokenSecret.parse(token.substring(1))).isEmpty();
        assertThat(TokenSecret.parse(token + "0")).isEmpty();
        assertThat(TokenSecret.parse("bkx_" + token.substring(4))).isEmpty();
        assertThat(TokenSecret.parse(" " + token.substring(1))).isEmpty();
    }

    @Test
    void doesNotMatchAnotherTokensHash() {
        TokenSecret a = TokenSecret.generate(TokenKind.PERSONAL);
        TokenSecret b = TokenSecret.generate(TokenKind.PERSONAL);
        assertThat(a.matches(b.sha256())).isFalse();
    }

    @Test
    void generatesDistinctTokens() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(TokenSecret.generate(TokenKind.PERSONAL).reveal()))
                    .isTrue();
        }
    }

    @Test
    void theFormatIsStable() {
        TokenSecret golden = TokenSecret.parse(GOLDEN).orElseThrow();
        assertThat(golden.kind()).isEqualTo(TokenKind.PERSONAL);
        assertThat(golden.reveal()).isEqualTo(GOLDEN);
        // echo -n bkp_KilnGoldenToken0123456789abcde4Evm0Z | sha256sum
        assertThat(HexFormat.of().formatHex(golden.sha256()))
                .isEqualTo(HexFormat.of().formatHex(sha256(GOLDEN)));
    }

    @Test
    void everySingleCharacterTypoIsRejected() {
        // CRC32 detects every error burst of up to 32 bits, so every single substitution is caught.
        for (int position = 4; position < GOLDEN.length(); position++) {
            for (int k = 0; k < ALPHABET.length(); k++) {
                char c = ALPHABET.charAt(k);
                if (c == GOLDEN.charAt(position)) {
                    continue;
                }
                String typo = GOLDEN.substring(0, position) + c + GOLDEN.substring(position + 1);
                assertThat(TokenSecret.parse(typo)).as(typo).isEmpty();
            }
        }
    }

    @Test
    void swappingThePrefixGivesADifferentSecret() {
        // The checksum covers only the random part, so the swap parses, but it hashes differently and
        // so never matches the stored token.
        TokenSecret personal = TokenSecret.parse(GOLDEN).orElseThrow();
        TokenSecret swapped = TokenSecret.parse("bks_" + GOLDEN.substring(4)).orElseThrow();
        assertThat(swapped.kind()).isEqualTo(TokenKind.SERVICE);
        assertThat(swapped.matches(personal.sha256())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "bkp_",
                "BKP_KilnGoldenToken0123456789abcde4Evm0Z",
                "bkp-KilnGoldenToken0123456789abcde4Evm0Z",
                "bkp_KilnGoldenToken0123456789abcde4Evm0Z\n",
                "bkp_KilnGoldenToken0123456789abcde4Evm0Z\u0000",
                " bkp_KilnGoldenToken0123456789abcde4Evm0Z",
                "Bearer bkp_KilnGoldenToken0123456789abcde4Evm0Z",
                "bkp_KilnGoldenToken0123456789abcde4Evm0",
                "bkp_KilnGoldenToken0123456789abcde4Evm0Z0",
                "bkp_KilnGoldenToken0123456789abcd\u04354Evm0Z", // Cyrillic e
                "bkp_KilnGoldenToken0123456789abcde\uFF14Evm0Z", // fullwidth 4
                "bkp_KilnGoldenToken01234567+9abcde4Evm0Z",
                "bkp_KilnGoldenToken01234567/9abcde4Evm0Z",
                "bkp_KilnGoldenToken01234567=9abcde4Evm0Z",
                "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"
            })
    void rejectsMalformedTokens(String presented) {
        assertThat(TokenSecret.parse(presented)).isEmpty();
    }

    @Test
    void rejectsHugeInputsQuickly() {
        String huge = "bkp_" + "a".repeat(10_000_000);
        assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> assertThat(TokenSecret.parse(huge)).isEmpty());
    }

    @Test
    void matchesOnlyTheExactHash() {
        TokenSecret token = TokenSecret.generate(TokenKind.SERVICE);
        byte[] hash = token.sha256();

        assertThat(token.matches(hash)).isTrue();
        assertThat(token.matches(new byte[0])).isFalse();
        assertThat(token.matches(Arrays.copyOf(hash, 31))).isFalse();
        assertThat(token.matches(Arrays.copyOf(hash, 33))).isFalse();
        byte[] flipped = hash.clone();
        flipped[31] ^= 1;
        assertThat(token.matches(flipped)).isFalse();
        assertThat(token.matches(token.reveal().getBytes(StandardCharsets.US_ASCII)))
                .isFalse();
    }

    @Test
    void theHashIsACopy() {
        TokenSecret token = TokenSecret.generate(TokenKind.PERSONAL);
        byte[] hash = token.sha256();
        hash[0] ^= 1;
        assertThat(token.matches(token.sha256())).isTrue();
        assertThat(token.sha256()).isNotEqualTo(hash);
    }

    @Test
    void neverRevealsAServiceTokenInToString() {
        TokenSecret token = TokenSecret.generate(TokenKind.SERVICE);
        assertThat(token).hasToString("bks_<redacted>");
        assertThat(String.valueOf(List.of(token))).doesNotContain(token.reveal());
    }

    @Test
    void secretScannersFindLeakedTokens() {
        String leaked = TokenSecret.generate(TokenKind.SERVICE).reveal();
        String log = "export KILN_TOKEN=" + leaked + "; npm publish";
        var matcher = TokenSecret.PATTERN.matcher(log);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo(leaked);
        assertThat(TokenSecret.parse(matcher.group())).isPresent();
    }

    @Test
    void randomCharactersAreUniform() {
        Map<Character, Integer> counts = new HashMap<>();
        int tokens = 20_000;
        for (int i = 0; i < tokens; i++) {
            String random = TokenSecret.generate(TokenKind.PERSONAL).reveal().substring(4, 34);
            for (int k = 0; k < random.length(); k++) {
                char c = random.charAt(k);
                counts.merge(c, 1, Integer::sum);
            }
        }
        assertThat(counts.keySet()).hasSize(62);
        double expected = tokens * 30 / 62.0;
        // Rejection sampling keeps the modulo bias out; allow 5% either way at this sample size.
        assertThat(counts.values()).allSatisfy(n -> assertThat((double) n).isBetween(expected * 0.95, expected * 1.05));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @FuzzTest
    void parseNeverThrows(FuzzedDataProvider data) {
        TokenSecret.parse(data.consumeRemainingAsString());
    }
}
