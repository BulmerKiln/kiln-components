/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth2 connectors declared in boot configuration, validated by {@link OAuth2Connector#from}. */
@ConfigurationProperties("kiln.auth.oauth2")
public record OAuth2Properties(@Nullable List<Connector> connectors) {

    public List<Connector> connectorList() {
        return connectors == null ? List.of() : connectors;
    }

    /**
     * One connector.
     *
     * @param issuer the namespace subjects live in, such as {@code https://github.com}; it keeps two
     *     IdPs' user IDs apart
     * @param urls the IdP's endpoints; a preset fills any left out
     * @param paths where the user-info response holds each value; a preset fills any left out
     * @param clientSecret a secret reference such as {@code env:KILN_GITHUB_SECRET}, never the secret
     */
    public record Connector(
            @Nullable String id,
            @Nullable String displayName,
            @Nullable OAuth2Preset preset,
            @Nullable String issuer,
            @Nullable Urls urls,
            @Nullable Paths paths,
            @Nullable List<String> scopes,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @Nullable List<String> domains) {}

    /**
     * The IdP's endpoints.
     *
     * @param allowInsecure allows {@code http} endpoints, for a local IdP only
     */
    public record Urls(
            @Nullable URI authorization,
            @Nullable URI token,
            @Nullable URI userInfo,
            boolean allowInsecure) {}

    /**
     * Where the user-info response holds each value, as dotted paths such as {@code data.user.id}.
     *
     * @param email used only together with {@code emailVerified}
     */
    public record Paths(
            @Nullable String userId,
            @Nullable String email,
            @Nullable String emailVerified,
            @Nullable String groups) {}
}
