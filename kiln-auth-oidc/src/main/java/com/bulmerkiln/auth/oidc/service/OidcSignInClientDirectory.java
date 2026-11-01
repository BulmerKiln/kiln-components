/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.service;

import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import com.bulmerkiln.auth.connector.util.SecretResolver;
import com.bulmerkiln.auth.oidc.OidcConnector;
import com.bulmerkiln.auth.oidc.domain.OidcEndpoints;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import lombok.Value;

/**
 * The OIDC connectors' sign-in clients. Secrets are read at boot, so an unreadable one stops it
 * (AUTH-OIDC-6). Endpoints come from each issuer's discovery document, fetched on the first sign-in
 * with that connector rather than at boot, so an IdP that's down doesn't stop Kiln starting. A
 * fetched document is kept; a failed fetch is tried again on the next sign-in (AUTH-OIDC-7).
 */
public final class OidcSignInClientDirectory implements SignInClientDirectory {

    private final Map<String, Configured> connectors = new HashMap<>();
    private final Function<URI, OidcEndpoints> discovery;
    private final ConcurrentMap<String, SignInClient> clients = new ConcurrentHashMap<>();

    /**
     * @param discovery fetches and checks an issuer's discovery document; it must refuse a document
     *     whose {@code issuer} isn't exactly the one asked for
     */
    public OidcSignInClientDirectory(List<OidcConnector> connectors, Function<URI, OidcEndpoints> discovery) {
        for (OidcConnector connector : connectors) {
            String secret = SecretResolver.resolveClientSecret(connector.getId(), connector.getClientSecret());
            this.connectors.put(connector.getId().getValue(), new Configured(connector, secret));
        }
        this.discovery = discovery;
    }

    @Override
    public Optional<SignInClient> find(String connectorId) {
        Configured configured = connectors.get(connectorId);
        if (configured == null) {
            return Optional.empty();
        }
        // computeIfAbsent keeps nothing when discovery throws, so the next sign-in tries again.
        return Optional.of(clients.computeIfAbsent(connectorId, id -> client(configured)));
    }

    private SignInClient client(Configured configured) {
        OidcConnector connector = configured.getConnector();
        OidcEndpoints endpoints = discovery.apply(connector.getIssuer());
        return new SignInClient(
                connector.getId(),
                connector.getClientId(),
                configured.getSecret(),
                connector.getScopes(),
                endpoints.getAuthorization(),
                endpoints.getToken(),
                endpoints.getUserInfo(),
                endpoints.getJwkSet(),
                connector.getIssuer());
    }

    /** A connector with its secret, read at boot. */
    @Value
    private static final class Configured {

        private final OidcConnector connector;

        private final String secret;
    }
}
