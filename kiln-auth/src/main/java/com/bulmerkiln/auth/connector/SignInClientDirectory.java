/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import com.bulmerkiln.auth.connector.domain.SignInClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One connector module's sign-in clients, found by connector ID. A module may build them at boot,
 * or on first use, such as after OpenID Connect discovery, so an IdP that's down doesn't stop Kiln
 * starting (AUTH-18).
 */
@FunctionalInterface
public interface SignInClientDirectory {

    /**
     * The client for {@code connectorId}, which comes from the request path and so may be anything,
     * or empty when it isn't one of this module's connectors.
     *
     * @throws RuntimeException when the client is this module's but can't be built now, such as when
     *     the IdP's discovery document can't be fetched; the next call tries again
     */
    Optional<SignInClient> find(String connectorId);

    /** A directory of clients built at boot. Connector IDs must be unique. */
    static SignInClientDirectory of(List<SignInClient> clients) {
        Map<String, SignInClient> byId = new HashMap<>();
        for (SignInClient client : clients) {
            if (byId.putIfAbsent(client.getId().getValue(), client) != null) {
                throw new IllegalArgumentException("Connector " + client.getId() + " is configured twice");
            }
        }
        Map<String, SignInClient> found = Map.copyOf(byId);
        return connectorId -> Optional.ofNullable(found.get(connectorId));
    }
}
