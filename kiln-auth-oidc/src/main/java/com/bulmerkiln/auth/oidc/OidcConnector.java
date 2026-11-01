/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.domain.IdpResponse;
import com.bulmerkiln.auth.connector.domain.SecretRef;
import com.bulmerkiln.auth.connector.util.DomainUtils;
import com.bulmerkiln.auth.connector.util.EndpointUtils;
import com.bulmerkiln.auth.oidc.autoconfigure.OidcProperties;
import com.bulmerkiln.auth.oidc.constant.OidcPreset;
import com.bulmerkiln.auth.oidc.service.OidcClaimsMapper;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** A validated OIDC connector. */
@Value
public final class OidcConnector implements IdentityConnector {

    private final ConnectorId id;

    private final String displayName;

    private final OidcPreset preset;

    private final URI issuer;

    private final String clientId;

    private final SecretRef clientSecret;

    private final List<String> scopes;

    private final @Nullable String groupsClaim;

    private final Set<String> domains;

    public OidcConnector(
            ConnectorId id,
            String displayName,
            OidcPreset preset,
            URI issuer,
            String clientId,
            SecretRef clientSecret,
            List<String> scopes,
            @Nullable String groupsClaim,
            Set<String> domains) {
        Objects.requireNonNull(id, "id");
        this.scopes = List.copyOf(scopes);
        this.domains = Set.copyOf(domains);
        if (!this.scopes.contains("openid")) {
            throw new IllegalArgumentException("Connector " + id + ": scopes must include openid");
        }
        if (preset.requiresHostedDomain() && this.domains.isEmpty()) {
            throw new IllegalArgumentException("Connector " + id + ": the " + preset + " preset needs domains");
        }
        this.id = id;
        this.displayName = displayName;
        this.preset = preset;
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.groupsClaim = groupsClaim;
    }

    /** Validates a configured connector, naming the connector and setting in every error. */
    public static OidcConnector from(OidcProperties.Connector config) {
        ConnectorId id = new ConnectorId(required(config.getId(), "id", "?"));
        OidcPreset preset = config.getPreset() == null ? OidcPreset.GENERIC : config.getPreset();
        URI issuer = EndpointUtils.requireSecure(
                required(config.getIssuer(), "issuer", id),
                config.isAllowInsecureIssuer(),
                "Connector " + id + " issuer");
        if (issuer.getRawQuery() != null) {
            throw new IllegalArgumentException("Connector " + id + " issuer must not have a query");
        }
        return new OidcConnector(
                id,
                config.getDisplayName() == null ? id.getValue() : config.getDisplayName(),
                preset,
                issuer,
                required(config.getClientId(), "client-id", id),
                SecretRef.parse(required(config.getClientSecret(), "client-secret", id)),
                config.getScopes() == null ? preset.scopes() : config.getScopes(),
                config.getGroupsClaim() == null ? preset.groupsClaim() : config.getGroupsClaim(),
                DomainUtils.normalize(config.getDomains() == null ? List.of() : config.getDomains()));
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
        return OidcClaimsMapper.map(this, response.getClaims());
    }

    private static <T> T required(@Nullable T value, String setting, Object id) {
        if (value == null) {
            throw new IllegalArgumentException("Connector " + id + ": " + setting + " is required");
        }
        return value;
    }
}
