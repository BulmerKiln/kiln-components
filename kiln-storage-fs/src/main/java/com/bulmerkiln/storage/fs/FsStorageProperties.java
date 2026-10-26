/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the filesystem blob store, used when {@code kiln.storage.type} is {@code fs}.
 *
 * @param root the directory that holds every organization's blobs and uploads. With more than one
 *     replica it must be a shared filesystem with atomic rename (NFS, CephFS)
 */
@ConfigurationProperties("kiln.storage.fs")
public record FsStorageProperties(@Nullable Path root) {}
