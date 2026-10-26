/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

/** A range outside the representation, answered with 416 and its size. */
public class RangeNotSatisfiableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long size;

    public RangeNotSatisfiableException(long size) {
        super("Range not satisfiable for " + size + " bytes");
        this.size = size;
    }

    /** The representation size, for the {@code Content-Range} header of the 416 response. */
    public long size() {
        return size;
    }
}
