/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

/**
 * Thrown by format code to refuse a request. The web layer turns it into a response with
 * {@link FormatResponse#refusal}; no cause or stack trace ever reaches the client.
 */
public class RefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Refusal refusal;

    public RefusedException(Refusal refusal) {
        super(refusal.message());
        this.refusal = refusal;
    }

    public Refusal refusal() {
        return refusal;
    }

    public int status() {
        return refusal.reason().status();
    }
}
