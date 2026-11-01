/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto;

/** A value that can't be sealed or opened. The message never carries key material or plaintext. */
public class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CryptoException(String message) {
        super(message);
    }
}
