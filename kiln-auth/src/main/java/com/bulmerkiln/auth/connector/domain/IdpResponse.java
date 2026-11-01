/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.domain;

import com.bulmerkiln.auth.connector.IdentityConnector;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * What an IdP returned, after the sign-in flow verified it.
 */
@Value
public final class IdpResponse {

    /**
     * The verified ID token claims (OIDC), user-info response (OAuth2) or assertion attributes (SAML), as parsed JSON.
     */
    private final Map<String, ?> claims;

    /**
     * The parsed results of the connector's {@link IdentityConnector#lookups()}, by name; a lookup
     * that failed is absent, which only ever means fewer groups.
     */
    private final Map<String, ?> lookups;

    public IdpResponse(Map<String, ?> claims, Map<String, ?> lookups) {
        // JSON can hold nulls, which Map.copyOf refuses.
        this.claims = Collections.unmodifiableMap(new LinkedHashMap<>(claims));
        this.lookups = Collections.unmodifiableMap(new LinkedHashMap<>(lookups));
    }

    public static IdpResponse of(Map<String, ?> claims) {
        return new IdpResponse(claims, Map.of());
    }
}
