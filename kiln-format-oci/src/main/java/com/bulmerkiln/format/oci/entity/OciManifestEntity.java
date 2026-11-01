/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code oci_manifests}: a manifest's media types and subject; its bytes are the file. */
@Value
@Table("oci_manifests")
public final class OciManifestEntity {

    @Id
    private final OciVersionKey id;

    private final UUID packageId;

    private final String mediaType;

    private final @Nullable String artifactType;

    private final @Nullable String subject;

    private final UUID fileId;
}
