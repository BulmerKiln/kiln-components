/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

/** Proves the contract suite isn't shaped around any one backend. */
class InMemoryBlobStoreTest extends BlobStoreContract {

    private final InMemoryBlobStore.State state = new InMemoryBlobStore.State();

    @Override
    protected BlobStore newStore() {
        return new InMemoryBlobStore(state);
    }
}
