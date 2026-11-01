/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.domain;

import lombok.Value;

/** An identity as an IdP names it: both parts, since a subject alone can name different people at different IdPs. */
@Value
public final class IssuerSubject {

    private final String issuer;

    private final String subject;

    public IssuerSubject(String issuer, String subject) {
        if (issuer.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("An admin subject needs both an issuer and a subject");
        }
        this.issuer = issuer;
        this.subject = subject;
    }
}
