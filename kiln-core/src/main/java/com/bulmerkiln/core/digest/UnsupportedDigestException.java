/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

/** A well-formed digest whose algorithm Kiln doesn't accept. */
public class UnsupportedDigestException extends InvalidDigestException {

    private static final long serialVersionUID = 1L;

    public UnsupportedDigestException(String message) {
        super(message);
    }
}
