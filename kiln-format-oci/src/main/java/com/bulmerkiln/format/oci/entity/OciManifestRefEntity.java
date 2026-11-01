/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code oci_manifest_refs}: a blob ({@code blob}) or manifest ({@code manifest}) referenced. */
@Value
@Table("oci_manifest_refs")
public final class OciManifestRefEntity {

    @Id
    private final OciManifestRefKey id;

    private final String kind;
}
