/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.IdentityConnectorContract;
import com.bulmerkiln.auth.connector.IdpResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A generic connector reading nested paths, as Bitbucket or an older ADFS would need. */
class GenericOAuth2ConnectorContractTest extends IdentityConnectorContract {

    private static OAuth2Connector corp(String groupsPath) {
        return OAuth2Connector.from(new OAuth2Properties.Connector(
                "corp",
                "Corp SSO",
                null,
                "https://corp.example",
                new OAuth2Properties.Urls(
                        URI.create("https://corp.example/auth"),
                        URI.create("https://corp.example/token"),
                        URI.create("https://corp.example/me"),
                        false),
                new OAuth2Properties.Paths("data.id", "data.email", "data.verified", groupsPath),
                List.of("profile"),
                "kiln",
                "env:S",
                List.of("corp.example")));
    }

    @Override
    protected IdentityConnector connector() {
        return corp("data.groups");
    }

    @Override
    protected IdentityConnector connectorWithoutGroups() {
        return corp(null);
    }

    @Override
    protected IdpResponse response(Object subject, String email, List<String> groups) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", subject);
        if (email != null) {
            data.put("email", email);
            data.put("verified", true);
        }
        if (groups != null) {
            data.put("groups", groups);
        }
        return IdpResponse.of(Map.of("data", data));
    }

    @Override
    protected IdpResponse responseWithUnverifiedEmail(String subject, String email) {
        return IdpResponse.of(Map.of("data", Map.of("id", subject, "email", email, "verified", false)));
    }
}
