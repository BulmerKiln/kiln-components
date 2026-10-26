/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The sign-in subsystem's contract. Every connector (OIDC, OAuth2, and SAML and Entra ID in
 * kiln-cloud) implements {@link com.bulmerkiln.auth.connector.IdentityConnector}, offers its
 * connectors through a {@link com.bulmerkiln.auth.connector.ConnectorSource}, and passes
 * {@code IdentityConnectorContract} from this module's test fixtures. Each produces the same
 * outcome: an {@link com.bulmerkiln.auth.connector.ExternalIdentity} with the IdP's groups, under
 * the shared {@link com.bulmerkiln.auth.connector.IdentityRules}.
 *
 * <p>Connectors never grant roles. Groups map to teams, and teams hold roles, in kiln-permissions;
 * a person with no mapped groups gets no roles (AUTH-10, AUTH-11).
 */
@NullMarked
package com.bulmerkiln.auth.connector;

import org.jspecify.annotations.NullMarked;
