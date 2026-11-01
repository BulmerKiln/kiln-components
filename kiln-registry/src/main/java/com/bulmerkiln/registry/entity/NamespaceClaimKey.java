/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;

/** A claim's key: one value per format in an organization, whichever namespace claims it. */
@Value
public final class NamespaceClaimKey {

    private final UUID organizationId;

    private final String format;

    private final String value;
}
