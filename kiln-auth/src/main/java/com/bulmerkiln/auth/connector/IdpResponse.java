/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What an IdP returned, after the sign-in flow verified it.
 *
 * @param claims the verified ID token claims (OIDC), user-info response (OAuth2) or assertion
 *     attributes (SAML), as parsed JSON
 * @param lookups the parsed results of the connector's {@link IdentityConnector#lookups()}, by
 *     name; a lookup that failed is absent, which only ever means fewer groups
 */
public record IdpResponse(Map<String, ?> claims, Map<String, ?> lookups) {

    public IdpResponse {
        // JSON can hold nulls, which Map.copyOf refuses.
        claims = Collections.unmodifiableMap(new LinkedHashMap<>(claims));
        lookups = Collections.unmodifiableMap(new LinkedHashMap<>(lookups));
    }

    public static IdpResponse of(Map<String, ?> claims) {
        return new IdpResponse(claims, Map.of());
    }
}
