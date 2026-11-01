/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code oci_upload_sessions}: a session and the repository that started it. */
@Value
@Table("oci_upload_sessions")
public final class OciUploadSessionEntity {

    @Id
    private final RegistryKey id;

    private final UUID packageId;
}
