/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.regex.Pattern;

/** The registered digest algorithms Kiln accepts (OCI image-spec, descriptor.md "Registered algorithms"). */
public enum DigestAlgorithm {
    SHA256("sha256", "SHA-256", 64),
    SHA512("sha512", "SHA-512", 128);

    private final String id;
    private final String jcaName;
    private final Pattern encoded;

    DigestAlgorithm(String id, String jcaName, int hexLength) {
        this.id = id;
        this.jcaName = jcaName;
        this.encoded = Pattern.compile("[a-f0-9]{" + hexLength + "}");
    }

    /** The identifier used in digest strings, such as {@code sha256}. */
    public String id() {
        return id;
    }

    public static Optional<DigestAlgorithm> fromId(String id) {
        for (DigestAlgorithm algorithm : values()) {
            if (algorithm.id.equals(id)) {
                return Optional.of(algorithm);
            }
        }
        return Optional.empty();
    }

    boolean isValidEncoding(String hex) {
        return encoded.matcher(hex).matches();
    }

    MessageDigest newMessageDigest() {
        try {
            return MessageDigest.getInstance(jcaName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides " + jcaName, e);
        }
    }
}
