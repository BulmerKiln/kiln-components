/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.domain;

import java.net.URI;
import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * An IdP's endpoints, from its OpenID Connect discovery document.
 */
@Value
public final class OidcEndpoints {

    private final URI authorization;

    private final URI token;

    /** The user-info endpoint, which the specification makes optional. */
    private final @Nullable URI userInfo;

    private final URI jwkSet;

    public OidcEndpoints(URI authorization, URI token, @Nullable URI userInfo, URI jwkSet) {
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(jwkSet, "jwkSet");
        this.authorization = authorization;
        this.token = token;
        this.userInfo = userInfo;
        this.jwkSet = jwkSet;
    }
}
