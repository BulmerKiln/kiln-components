/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.token;

import com.bulmerkiln.core.annotation.UntrustedInput;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A random secret that only a browser holds, such as a session ID or an invite link's token: 256
 * bits from {@link SecureRandom}, as 43 base64url characters. Kiln keeps only its SHA-256, so a
 * copy of the database can't be replayed (AUTH-21, AUTH-26). {@link #toString()} never reveals it.
 */
public final class OpaqueSecret {

    private static final int BYTES = 32;
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    private OpaqueSecret(String value) {
        this.value = value;
    }

    public static OpaqueSecret generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new OpaqueSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** Accepts a presented secret only in the exact shape {@link #generate()} produces. */
    @UntrustedInput
    public static Optional<OpaqueSecret> parse(String presented) {
        if (presented == null || !SHAPE.matcher(presented).matches()) {
            return Optional.empty();
        }
        return Optional.of(new OpaqueSecret(presented));
    }

    /** The secret itself, to hand to the browser once. */
    public String reveal() {
        return value;
    }

    /** What Kiln stores and looks up. */
    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JDK has SHA-256", e);
        }
    }

    @Override
    public String toString() {
        return "OpaqueSecret[redacted]";
    }
}
