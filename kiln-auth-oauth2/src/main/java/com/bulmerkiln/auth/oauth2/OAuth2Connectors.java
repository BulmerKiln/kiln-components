/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.ConnectorSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every OAuth2 connector from boot configuration, validated at startup. */
public record OAuth2Connectors(List<OAuth2Connector> connectors) implements ConnectorSource {

    public OAuth2Connectors {
        connectors = List.copyOf(connectors);
        Set<String> ids = new HashSet<>();
        for (OAuth2Connector connector : connectors) {
            if (!ids.add(connector.id().value())) {
                throw new IllegalArgumentException("Duplicate connector ID: " + connector.id());
            }
        }
    }

    public static OAuth2Connectors from(OAuth2Properties properties) {
        return new OAuth2Connectors(
                properties.connectorList().stream().map(OAuth2Connector::from).toList());
    }
}
