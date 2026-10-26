/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.List;
import java.util.Optional;

/**
 * The connectors people can sign in with: from boot configuration in kiln-oss, and per
 * organization in kiln-cloud (AUTH-13).
 */
public interface ConnectorRegistry {

    List<IdentityConnector> connectors();

    Optional<IdentityConnector> get(ConnectorId id);

    /** The connectors that serve an email domain, for IdP discovery. */
    List<IdentityConnector> forDomain(String domain);
}
