/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs.autoconfigure;

import java.nio.file.Path;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the filesystem blob store, used when {@code kiln.storage.type} is {@code fs}.
 */
@ConfigurationProperties("kiln.storage.fs")
@Value
public final class FsStorageProperties {

    /**
     * The directory that holds every organization's blobs and uploads. With more than one replica it
     * must be a shared filesystem with atomic rename (NFS, CephFS).
     */
    private final @Nullable Path root;
}
