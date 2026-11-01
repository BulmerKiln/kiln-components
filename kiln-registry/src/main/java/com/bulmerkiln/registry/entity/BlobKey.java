/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;

/** A blob's key: its digest within the organization. */
@Value
public final class BlobKey {

    private final UUID organizationId;

    private final String digest;
}
