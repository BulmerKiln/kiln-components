/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A content digest such as {@code sha256:6c3c...}, validated to the OCI image-spec grammar
 * (descriptor.md, "Digests"). Only registered algorithms are accepted, with their exact lowercase
 * hex encodings, so a {@code Digest} is always safe to use in a storage path.
 */
public record Digest(DigestAlgorithm algorithm, String encoded) {

    /** Longer than any registered digest; checked before matching so no input is unbounded. */
    private static final int MAX_LENGTH = 256;

    /**
     * The image-spec grammar, {@code algorithm ":" encoded}, where an algorithm is components
     * joined by one of {@code + . _ -}. Checked part by part, so no pattern can backtrack.
     */
    private static final Pattern SEPARATOR = Pattern.compile("[+._-]");

    private static final Pattern COMPONENT = Pattern.compile("[a-z0-9]+");
    private static final Pattern ENCODED = Pattern.compile("[a-zA-Z0-9=_-]+");

    public Digest {
        Objects.requireNonNull(algorithm, "algorithm");
        if (!algorithm.isValidEncoding(encoded)) {
            throw new InvalidDigestException("Invalid " + algorithm.id() + " encoding");
        }
    }

    /**
     * Parses a digest string.
     *
     * @throws UnsupportedDigestException if it's well formed but uses an unregistered algorithm
     * @throws InvalidDigestException if it's malformed
     */
    public static Digest parse(String value) {
        int colon = value.indexOf(':');
        if (value.length() > MAX_LENGTH
                || colon < 0
                || !isAlgorithm(value.substring(0, colon))
                || !ENCODED.matcher(value.substring(colon + 1)).matches()) {
            throw new InvalidDigestException("Malformed digest");
        }
        String id = value.substring(0, colon);
        DigestAlgorithm algorithm = DigestAlgorithm.fromId(id)
                .orElseThrow(() -> new UnsupportedDigestException("Unsupported digest algorithm: " + id));
        return new Digest(algorithm, value.substring(colon + 1));
    }

    private static boolean isAlgorithm(String algorithm) {
        for (String component : SEPARATOR.split(algorithm, -1)) {
            if (!COMPONENT.matcher(component).matches()) {
                return false;
            }
        }
        return true;
    }

    public static Digest sha256(byte[] content) {
        Digester digester = Digester.of(DigestAlgorithm.SHA256);
        digester.update(content, 0, content.length);
        return digester.digest();
    }

    @Override
    public String toString() {
        return algorithm.id() + ":" + encoded;
    }
}
