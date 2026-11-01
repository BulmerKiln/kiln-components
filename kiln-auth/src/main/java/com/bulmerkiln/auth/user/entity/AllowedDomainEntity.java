/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.entity;

import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import java.util.UUID;
import lombok.Value;

/** A verified email at {@code domain}, through {@code connectorId}, creates a user who joins the organization. */
@Value
public final class AllowedDomainEntity {

    private final String connectorId;

    private final String domain;

    private final UUID organizationId;

    private final AllowedDomainSource source;
}
