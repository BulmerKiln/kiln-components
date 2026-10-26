/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.List;

/** A module's connectors, such as every OIDC connector in boot configuration. */
public interface ConnectorSource {

    List<? extends IdentityConnector> connectors();
}
