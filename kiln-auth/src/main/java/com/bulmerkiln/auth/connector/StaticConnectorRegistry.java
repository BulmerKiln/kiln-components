/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Connectors from boot configuration, from every connector module together. Their IDs must be
 * unique across protocols, since the ID alone picks the callback route and the remembered IdP.
 */
public final class StaticConnectorRegistry implements ConnectorRegistry {

    private static final Pattern LOOKUP_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private final Map<ConnectorId, IdentityConnector> connectors = new LinkedHashMap<>();

    public StaticConnectorRegistry(Collection<? extends IdentityConnector> connectors) {
        for (IdentityConnector connector : connectors) {
            for (Map.Entry<String, URI> lookup : connector.lookups().entrySet()) {
                if (!LOOKUP_NAME.matcher(lookup.getKey()).matches()) {
                    throw new IllegalStateException("Connector " + connector.id() + " has an invalid lookup name");
                }
                Endpoints.requireSecure(
                        lookup.getValue(), false, "Connector " + connector.id() + " lookup " + lookup.getKey());
            }
            IdentityConnector previous = this.connectors.putIfAbsent(connector.id(), connector);
            if (previous != null) {
                throw new IllegalStateException("Connector ID " + connector.id() + " is used by both a "
                        + previous.type() + " and a " + connector.type() + " connector");
            }
        }
    }

    @Override
    public List<IdentityConnector> connectors() {
        return List.copyOf(connectors.values());
    }

    @Override
    public Optional<IdentityConnector> get(ConnectorId id) {
        return Optional.ofNullable(connectors.get(id));
    }

    @Override
    public List<IdentityConnector> forDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT);
        return connectors.values().stream()
                .filter(c -> c.domains().contains(lower))
                .toList();
    }
}
