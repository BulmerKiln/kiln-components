/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.entity;

import java.util.UUID;
import lombok.Value;

/** The key of a version's npm row: the registry version it extends. */
@Value
public final class NpmVersionKey {

    private final UUID organizationId;

    private final UUID versionId;
}
