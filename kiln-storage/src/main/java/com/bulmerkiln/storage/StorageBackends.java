/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** Picks the configured backend, failing with a message an installer can act on. */
public final class StorageBackends {

    private StorageBackends() {}

    public static StorageBackend select(@Nullable String type, Collection<StorageBackend> backends) {
        Map<String, StorageBackend> byType = new TreeMap<>();
        for (StorageBackend backend : backends) {
            if (byType.putIfAbsent(backend.type(), backend) != null) {
                throw new IllegalStateException("Two storage backends both have type " + backend.type());
            }
        }
        if (byType.isEmpty()) {
            throw new IllegalStateException(
                    "No storage backend is on the classpath; add kiln-storage-fs or kiln-storage-s3");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("kiln.storage.type must be set to one of " + byType.keySet());
        }
        StorageBackend backend = byType.get(type);
        if (backend == null) {
            throw new IllegalStateException(
                    "kiln.storage.type is " + type + ", but the available backends are " + byType.keySet());
        }
        return backend;
    }
}
