/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.entity;

import java.util.UUID;
import lombok.Value;

/** A dist-tag's key: its name within the package. */
@Value
public final class NpmDistTagKey {

    private final UUID organizationId;

    private final UUID packageId;

    private final String tag;
}
