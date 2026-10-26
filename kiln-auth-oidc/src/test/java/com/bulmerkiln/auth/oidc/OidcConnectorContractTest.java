/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.IdentityConnectorContract;
import com.bulmerkiln.auth.connector.IdpResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class OidcConnectorContractTest extends IdentityConnectorContract {

    private static final String ISSUER = "https://sso.acme.com/realms/acme";

    @Override
    protected IdentityConnector connector() {
        return OidcConnector.from(OidcConnectorTest.config("sso", OidcPreset.KEYCLOAK, ISSUER, List.of("acme.com")));
    }

    @Override
    protected IdentityConnector connectorWithoutGroups() {
        return OidcConnector.from(new OidcProperties.Connector(
                "google",
                null,
                OidcPreset.GOOGLE_WORKSPACE,
                URI.create(ISSUER),
                "kiln",
                "env:S",
                null,
                null,
                List.of("acme.com"),
                false));
    }

    @Override
    protected IdpResponse response(Object subject, String email, List<String> groups) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", subject);
        claims.put("hd", "acme.com");
        if (email != null) {
            claims.put("email", email);
            claims.put("email_verified", true);
        }
        if (groups != null) {
            claims.put("groups", groups);
        }
        return IdpResponse.of(claims);
    }

    @Override
    protected IdpResponse responseWithUnverifiedEmail(String subject, String email) {
        return IdpResponse.of(Map.of("iss", ISSUER, "sub", subject, "email", email, "email_verified", false));
    }
}
