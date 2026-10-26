/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.token;

/** The two kinds of long-lived token, the bottom rung of the credential ladder (AUTH-1). */
public enum TokenKind {
    PERSONAL("bkp_"),
    SERVICE("bks_");

    private final String prefix;

    TokenKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
