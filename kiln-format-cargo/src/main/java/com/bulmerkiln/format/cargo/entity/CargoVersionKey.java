/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.entity;

import java.util.UUID;
import lombok.Value;

/** The key of a version's Cargo row: the registry version it extends. */
@Value
public final class CargoVersionKey {

    private final UUID organizationId;

    private final UUID versionId;
}
