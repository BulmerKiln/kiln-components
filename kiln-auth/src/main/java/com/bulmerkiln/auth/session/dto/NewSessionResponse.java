/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.dto;

import com.bulmerkiln.auth.session.entity.WebSessionEntity;
import com.bulmerkiln.auth.token.OpaqueSecret;
import lombok.Value;

/** A new session, with its ID for the cookie: the only time the ID exists outside the browser. */
@Value
public final class NewSessionResponse {

    private final OpaqueSecret id;

    private final WebSessionEntity session;
}
