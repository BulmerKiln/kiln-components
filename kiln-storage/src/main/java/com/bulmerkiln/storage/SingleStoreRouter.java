/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.organization.Organization;
import java.util.Objects;

/** Routes every organization to one store, which keeps them apart by key. */
public record SingleStoreRouter(BlobStore blobStore) implements StorageRouter {

    public SingleStoreRouter {
        Objects.requireNonNull(blobStore, "blobStore");
    }

    @Override
    public BlobStore store(Organization organization) {
        return blobStore;
    }
}
