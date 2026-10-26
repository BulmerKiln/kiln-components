/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

/**
 * An IdP response that can't be turned into an identity. Sign-in fails; the message is for logs
 * and never contains claim values.
 */
public class InvalidIdentityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidIdentityException(String message) {
        super(message);
    }
}
