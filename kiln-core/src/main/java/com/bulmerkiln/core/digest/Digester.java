/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Computes a {@link Digest} over streamed content. Not thread-safe. */
public final class Digester {

    private final DigestAlgorithm algorithm;
    private final MessageDigest messageDigest;

    private Digester(DigestAlgorithm algorithm) {
        this.algorithm = algorithm;
        this.messageDigest = algorithm.newMessageDigest();
    }

    public static Digester of(DigestAlgorithm algorithm) {
        return new Digester(algorithm);
    }

    public void update(byte[] bytes, int offset, int length) {
        messageDigest.update(bytes, offset, length);
    }

    /** Reads {@code in} to the end, digesting every byte. */
    public void update(InputStream in) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            messageDigest.update(buffer, 0, read);
        }
    }

    /** Completes the digest. The digester can't be used afterwards. */
    public Digest digest() {
        return new Digest(algorithm, HexFormat.of().formatHex(messageDigest.digest()));
    }
}
