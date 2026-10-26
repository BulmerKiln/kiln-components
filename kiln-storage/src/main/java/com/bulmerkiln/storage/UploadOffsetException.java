/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

/** A chunk that doesn't start where the upload ends: a gap or an overlap. */
public class UploadOffsetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long expected;

    public UploadOffsetException(long expected, long actual) {
        super("Chunk starts at " + actual + " but the upload is " + expected + " bytes");
        this.expected = expected;
    }

    /** The offset the chunk had to start at, which the client should resume from. */
    public long expected() {
        return expected;
    }
}
