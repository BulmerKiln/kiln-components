/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs the contract against a minimal connector, so the suite itself is tested here. */
class ExampleConnectorContractTest extends IdentityConnectorContract {

    private static final ConnectorId ID = new ConnectorId("example");

    private static IdentityConnector example(boolean groups) {
        return new IdentityConnector() {
            @Override
            public ConnectorId id() {
                return ID;
            }

            @Override
            public String type() {
                return "example";
            }

            @Override
            public String displayName() {
                return "Example";
            }

            @Override
            public Set<String> domains() {
                return Set.of("acme.com");
            }

            @Override
            public boolean providesGroups() {
                return groups;
            }

            @Override
            public ExternalIdentity identify(IdpResponse response) {
                Map<String, ?> c = response.claims();
                String subject = IdentityRules.subject(c.get("sub") instanceof String s ? s : null);
                return new ExternalIdentity(
                        ID,
                        "https://idp.example",
                        subject,
                        IdentityRules.verifiedEmail(c.get("email"), c.get("email_verified")),
                        groups ? IdentityRules.groups(c.get("groups")) : Set.of());
            }
        };
    }

    @Override
    protected IdentityConnector connector() {
        return example(true);
    }

    @Override
    protected IdentityConnector connectorWithoutGroups() {
        return example(false);
    }

    @Override
    protected IdpResponse response(Object subject, String email, List<String> groups) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
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
        return IdpResponse.of(Map.of("sub", subject, "email", email, "email_verified", false));
    }
}
