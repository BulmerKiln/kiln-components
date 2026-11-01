/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code blobs}, with whichever checksums were taken as it was stored. */
@Value
@Table("blobs")
public final class BlobEntity {

    @Id
    private final BlobKey id;

    private final long size;

    private final @Nullable String md5;

    private final @Nullable String sha1;

    private final @Nullable String sha256;

    private final @Nullable String sha512;
}
