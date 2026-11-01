/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code oci_repository_blobs}: the repository may read the blob. */
@Value
@Table("oci_repository_blobs")
public final class OciRepositoryBlobEntity {

    @Id
    private final OciRepositoryBlobKey id;
}
