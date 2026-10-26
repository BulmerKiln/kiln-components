/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * A configured way to sign in. The protocol flow itself (redirects, PKCE, the token exchange) is
 * Spring Security's; a connector describes its IdP and turns the verified response into an
 * identity. Implementations must pass {@code IdentityConnectorContract}.
 */
public interface IdentityConnector {

    ConnectorId id();

    /** The protocol, such as {@code oidc} or {@code oauth2}, as stored in {@code connectors.type}. */
    String type();

    /** Shown on the sign-in page. */
    String displayName();

    /** The email domains this connector serves, for IdP discovery. Lowercase. */
    Set<String> domains();

    /**
     * Whether this connector can report groups. Without them, its people get no roles until an
     * admin adds them to a team, and the web app says teams for them are managed in Kiln.
     */
    boolean providesGroups();

    /**
     * Extra GETs the sign-in flow makes with the user's access token after the token exchange,
     * such as GitHub's {@code /user/teams}; each result is passed to {@link #identify} by name.
     * Every URL must be https.
     */
    default Map<String, URI> lookups() {
        return Map.of();
    }

    /**
     * Turns a verified IdP response into an identity, applying {@link IdentityRules}.
     *
     * @throws InvalidIdentityException if the response doesn't identify anyone usable
     */
    ExternalIdentity identify(IdpResponse response);
}
