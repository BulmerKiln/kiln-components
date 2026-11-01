/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;

/** What a manifest references, by the referencing manifest's version and the child's digest. */
@Value
public final class OciManifestRefKey {

    private final UUID organizationId;

    private final UUID versionId;

    private final String child;
}
