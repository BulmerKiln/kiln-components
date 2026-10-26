/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

/** An upload that would exceed its size limit. */
public class BlobTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long limit;

    public BlobTooLargeException(long limit) {
        super("Upload exceeds the limit of " + limit + " bytes");
        this.limit = limit;
    }

    public long limit() {
        return limit;
    }
}
