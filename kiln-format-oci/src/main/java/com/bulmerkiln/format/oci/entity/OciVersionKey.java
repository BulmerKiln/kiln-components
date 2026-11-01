/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;

/** The key of a manifest's own row: the registry version it extends. */
@Value
public final class OciVersionKey {

    private final UUID organizationId;

    private final UUID versionId;
}
