/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.service;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.StorageRouter;
import java.util.Objects;
import lombok.Value;

/** Routes every organization to one store, which keeps them apart by key. */
@Value
public final class SingleStoreRouter implements StorageRouter {

    private final BlobStore blobStore;

    public SingleStoreRouter(BlobStore blobStore) {
        Objects.requireNonNull(blobStore, "blobStore");
        this.blobStore = blobStore;
    }

    @Override
    public BlobStore store(Organization organization) {
        return blobStore;
    }
}
