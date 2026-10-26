/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC connectors declared in boot configuration ({@code managedBy: boot}). Bound as written and
 * validated by {@link OidcConnector#from}.
 */
@ConfigurationProperties("kiln.auth.oidc")
public record OidcProperties(@Nullable List<Connector> connectors) {

    public List<Connector> connectorList() {
        return connectors == null ? List.of() : connectors;
    }

    /**
     * One connector.
     *
     * @param clientSecret a secret reference such as {@code env:KILN_OIDC_SECRET}, never the secret
     * @param allowInsecureIssuer allows an {@code http} issuer, for a local IdP only
     */
    public record Connector(
            @Nullable String id,
            @Nullable String displayName,
            @Nullable OidcPreset preset,
            @Nullable URI issuer,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @Nullable List<String> scopes,
            @Nullable String groupsClaim,
            @Nullable List<String> domains,
            boolean allowInsecureIssuer) {}
}
