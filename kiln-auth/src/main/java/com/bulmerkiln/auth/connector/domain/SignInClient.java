/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.domain;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * What browser sign-in needs to run the authorization code flow with one connector: its client
 * credentials and the IdP's endpoints. The callback is always {@code /auth/<connector-id>/callback}.
 */
@Value
public final class SignInClient {

    private final ConnectorId id;

    private final String clientId;

    /** The resolved secret; never shown by {@link #toString()}. */
    private final String clientSecret;

    private final List<String> scopes;

    private final URI authorizationUri;

    private final URI tokenUri;

    /** The user-info endpoint, if the IdP has one. */
    private final @Nullable URI userInfoUri;

    /** The keys that sign ID tokens, for OpenID Connect. */
    private final @Nullable URI jwkSetUri;

    /** The issuer ID tokens must name, for OpenID Connect. */
    private final @Nullable URI issuer;

    public SignInClient(
            ConnectorId id,
            String clientId,
            String clientSecret,
            List<String> scopes,
            URI authorizationUri,
            URI tokenUri,
            @Nullable URI userInfoUri,
            @Nullable URI jwkSetUri,
            @Nullable URI issuer) {
        Objects.requireNonNull(id, "id");
        this.scopes = List.copyOf(scopes);
        this.id = id;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.jwkSetUri = jwkSetUri;
        this.issuer = issuer;
    }

    @Override
    public String toString() {
        return "SignInClient[id=" + id + ", clientId=" + clientId + ", clientSecret=(redacted)]";
    }
}
