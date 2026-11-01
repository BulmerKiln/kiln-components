/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;

/** A tag's key: its name within the repository's package. */
@Value
public final class OciTagKey {

    private final UUID organizationId;

    private final UUID packageId;

    private final String name;
}
