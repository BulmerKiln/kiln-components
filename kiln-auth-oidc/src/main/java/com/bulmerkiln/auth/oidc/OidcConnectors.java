/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.ConnectorSource;
import com.bulmerkiln.auth.oidc.autoconfigure.OidcProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

/** Every OIDC connector from boot configuration, validated at startup. */
@Value
public final class OidcConnectors implements ConnectorSource {

    private final List<OidcConnector> connectors;

    public OidcConnectors(List<OidcConnector> connectors) {
        this.connectors = List.copyOf(connectors);
        Set<String> ids = new HashSet<>();
        for (OidcConnector connector : this.connectors) {
            if (!ids.add(connector.getId().getValue())) {
                throw new IllegalArgumentException("Duplicate connector ID: " + connector.getId());
            }
        }
    }

    public static OidcConnectors from(OidcProperties properties) {
        return new OidcConnectors(
                properties.connectorList().stream().map(OidcConnector::from).toList());
    }
}
