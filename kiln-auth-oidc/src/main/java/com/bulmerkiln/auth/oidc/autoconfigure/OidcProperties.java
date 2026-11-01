/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.autoconfigure;

import com.bulmerkiln.auth.oidc.OidcConnector;
import com.bulmerkiln.auth.oidc.constant.OidcPreset;
import java.net.URI;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC connectors declared in boot configuration ({@code managedBy: boot}). Bound as written and
 * validated by {@link OidcConnector#from}.
 */
@ConfigurationProperties("kiln.auth.oidc")
@Value
public final class OidcProperties {

    private final @Nullable List<Connector> connectors;

    public List<Connector> connectorList() {
        return connectors == null ? List.of() : connectors;
    }

    /**
     * One connector.
     */
    @Value
    public static final class Connector {

        private final @Nullable String id;

        private final @Nullable String displayName;

        private final @Nullable OidcPreset preset;

        private final @Nullable URI issuer;

        private final @Nullable String clientId;

        /** A secret reference such as {@code env:KILN_OIDC_SECRET}, never the secret. */
        private final @Nullable String clientSecret;

        private final @Nullable List<String> scopes;

        private final @Nullable String groupsClaim;

        private final @Nullable List<String> domains;

        /** Allows an {@code http} issuer, for a local IdP only. */
        private final boolean allowInsecureIssuer;
    }
}
