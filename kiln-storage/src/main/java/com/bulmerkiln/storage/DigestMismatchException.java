/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.digest.Digest;

/** Uploaded content that doesn't match the digest the client declared. */
public class DigestMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DigestMismatchException(Digest expected, Digest actual) {
        super("Expected " + expected + " but the content is " + actual);
    }
}
