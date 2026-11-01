/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.ConnectorSource;
import com.bulmerkiln.auth.oauth2.autoconfigure.OAuth2Properties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

/** Every OAuth2 connector from boot configuration, validated at startup. */
@Value
public final class OAuth2Connectors implements ConnectorSource {

    private final List<OAuth2Connector> connectors;

    public OAuth2Connectors(List<OAuth2Connector> connectors) {
        this.connectors = List.copyOf(connectors);
        Set<String> ids = new HashSet<>();
        for (OAuth2Connector connector : this.connectors) {
            if (!ids.add(connector.getId().getValue())) {
                throw new IllegalArgumentException("Duplicate connector ID: " + connector.getId());
            }
        }
    }

    public static OAuth2Connectors from(OAuth2Properties properties) {
        return new OAuth2Connectors(
                properties.connectorList().stream().map(OAuth2Connector::from).toList());
    }
}
