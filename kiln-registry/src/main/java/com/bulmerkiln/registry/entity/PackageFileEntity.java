/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code package_files}: a named file of a version, and the blob holding its bytes. */
@Value
@Table("package_files")
public final class PackageFileEntity {

    @Id
    private final RegistryKey id;

    private final UUID versionId;

    private final String name;

    private final String digest;

    private final long size;
}
