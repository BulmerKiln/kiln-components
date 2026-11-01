/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;

/** A blob linked to a repository: the repository's package and the blob's digest. */
@Value
public final class OciRepositoryBlobKey {

    private final UUID organizationId;

    private final UUID packageId;

    private final String digest;
}
