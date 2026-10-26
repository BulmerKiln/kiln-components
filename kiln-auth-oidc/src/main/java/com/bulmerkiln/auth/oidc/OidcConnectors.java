/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.ConnectorSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every OIDC connector from boot configuration, validated at startup. */
public record OidcConnectors(List<OidcConnector> connectors) implements ConnectorSource {

    public OidcConnectors {
        connectors = List.copyOf(connectors);
        Set<String> ids = new HashSet<>();
        for (OidcConnector connector : connectors) {
            if (!ids.add(connector.id().value())) {
                throw new IllegalArgumentException("Duplicate connector ID: " + connector.id());
            }
        }
    }

    public static OidcConnectors from(OidcProperties properties) {
        return new OidcConnectors(
                properties.connectorList().stream().map(OidcConnector::from).toList());
    }
}
