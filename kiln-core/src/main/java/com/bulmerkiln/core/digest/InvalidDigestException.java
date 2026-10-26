/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

/** A digest string that isn't well formed; formats answer it with their invalid-digest error. */
public class InvalidDigestException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidDigestException(String message) {
        super(message);
    }
}
