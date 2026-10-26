/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.ConnectorId;
import com.bulmerkiln.auth.connector.Domains;
import com.bulmerkiln.auth.connector.Endpoints;
import com.bulmerkiln.auth.connector.ExternalIdentity;
import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.IdpResponse;
import com.bulmerkiln.auth.connector.SecretRef;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A validated OIDC connector. */
public record OidcConnector(
        ConnectorId id,
        String displayName,
        OidcPreset preset,
        URI issuer,
        String clientId,
        SecretRef clientSecret,
        List<String> scopes,
        @Nullable String groupsClaim,
        Set<String> domains)
        implements IdentityConnector {

    public OidcConnector {
        Objects.requireNonNull(id, "id");
        scopes = List.copyOf(scopes);
        domains = Set.copyOf(domains);
        if (!scopes.contains("openid")) {
            throw new IllegalArgumentException("Connector " + id + ": scopes must include openid");
        }
        if (preset.requiresHostedDomain() && domains.isEmpty()) {
            throw new IllegalArgumentException("Connector " + id + ": the " + preset + " preset needs domains");
        }
    }

    /** Validates a configured connector, naming the connector and setting in every error. */
    public static OidcConnector from(OidcProperties.Connector config) {
        ConnectorId id = new ConnectorId(required(config.id(), "id", "?"));
        OidcPreset preset = config.preset() == null ? OidcPreset.GENERIC : config.preset();
        URI issuer = Endpoints.requireSecure(
                required(config.issuer(), "issuer", id), config.allowInsecureIssuer(), "Connector " + id + " issuer");
        if (issuer.getRawQuery() != null) {
            throw new IllegalArgumentException("Connector " + id + " issuer must not have a query");
        }
        return new OidcConnector(
                id,
                config.displayName() == null ? id.value() : config.displayName(),
                preset,
                issuer,
                required(config.clientId(), "client-id", id),
                SecretRef.parse(required(config.clientSecret(), "client-secret", id)),
                config.scopes() == null ? preset.scopes() : config.scopes(),
                config.groupsClaim() == null ? preset.groupsClaim() : config.groupsClaim(),
                Domains.normalize(config.domains() == null ? List.of() : config.domains()));
    }

    @Override
    public String type() {
        return "oidc";
    }

    @Override
    public boolean providesGroups() {
        return groupsClaim != null;
    }

    /** Maps the verified ID token's claims; see {@link OidcClaimsMapper}. */
    @Override
    public ExternalIdentity identify(IdpResponse response) {
        return OidcClaimsMapper.map(this, response.claims());
    }

    private static <T> T required(@Nullable T value, String setting, Object id) {
        if (value == null) {
            throw new IllegalArgumentException("Connector " + id + ": " + setting + " is required");
        }
        return value;
    }
}
