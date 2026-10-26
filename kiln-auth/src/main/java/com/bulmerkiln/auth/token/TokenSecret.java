/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * A token secret, such as {@code bkp_} followed by 36 characters. It's shown to its owner once and
 * stored only as its SHA-256 hash (AUTH-2).
 *
 * <p>The format follows GitHub's, so leaks are detectable: a fixed prefix, 30 random base62
 * characters (178 bits) and a 6-character base62 CRC32 of them. Secret scanners match
 * {@link #PATTERN}, and the checksum rejects most typos and guesses before any database lookup.
 *
 * <p>{@link #toString()} never reveals the secret.
 */
public final class TokenSecret {

    /** The pattern for secret scanning and pre-commit hooks. */
    public static final Pattern PATTERN = Pattern.compile("bk[ps]_[0-9A-Za-z]{36}");

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_LENGTH = 30;
    private static final int CHECKSUM_LENGTH = 6;
    private static final int LENGTH = 4 + RANDOM_LENGTH + CHECKSUM_LENGTH;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenKind kind;
    private final String value;

    private TokenSecret(TokenKind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static TokenSecret generate(TokenKind kind) {
        StringBuilder random = new StringBuilder(RANDOM_LENGTH);
        while (random.length() < RANDOM_LENGTH) {
            // Rejection sampling keeps every character uniform: 62 of the 64 six-bit values are used.
            int sixBits = RANDOM.nextInt() & 0x3f;
            if (sixBits < BASE62.length()) {
                random.append(BASE62.charAt(sixBits));
            }
        }
        return new TokenSecret(kind, kind.prefix() + random + checksum(random));
    }

    /** Accepts a presented token only if it has a known prefix, the right shape and a valid checksum. */
    public static Optional<TokenSecret> parse(String presented) {
        if (presented.length() != LENGTH || !PATTERN.matcher(presented).matches()) {
            return Optional.empty();
        }
        TokenKind kind = presented.startsWith(TokenKind.PERSONAL.prefix()) ? TokenKind.PERSONAL : TokenKind.SERVICE;
        String random = presented.substring(4, 4 + RANDOM_LENGTH);
        String checksum = presented.substring(4 + RANDOM_LENGTH);
        if (!checksum.equals(checksum(random))) {
            return Optional.empty();
        }
        return Optional.of(new TokenSecret(kind, presented));
    }

    public TokenKind kind() {
        return kind;
    }

    /** The secret itself, to show its owner once at creation. */
    public String reveal() {
        return value;
    }

    /** The SHA-256 hash, which is all that's stored. */
    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
    }

    /** Compares against a stored hash in constant time. */
    public boolean matches(byte[] storedSha256) {
        return MessageDigest.isEqual(sha256(), storedSha256);
    }

    @Override
    public String toString() {
        return kind.prefix() + "<redacted>";
    }

    private static String checksum(CharSequence random) {
        CRC32 crc = new CRC32();
        crc.update(random.toString().getBytes(StandardCharsets.US_ASCII));
        long remaining = crc.getValue();
        char[] digits = new char[CHECKSUM_LENGTH];
        for (int i = CHECKSUM_LENGTH - 1; i >= 0; i--) {
            digits[i] = BASE62.charAt((int) (remaining % BASE62.length()));
            remaining /= BASE62.length();
        }
        return new String(digits);
    }
}
