/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code oci_tags}: a tag and the manifest version it points at. */
@Value
@Table("oci_tags")
public final class OciTagEntity {

    @Id
    private final OciTagKey id;

    private final UUID versionId;
}
