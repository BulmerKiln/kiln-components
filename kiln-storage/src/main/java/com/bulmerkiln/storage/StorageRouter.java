/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.storage.service.SingleStoreRouter;

/**
 * Finds the store that holds an organization's blobs. {@link SingleStoreRouter} uses one store for
 * every organization; an assembly can provide its own router instead (STO-13).
 */
public interface StorageRouter {

    BlobStore store(Organization organization);

    /** The store of the organization in {@link OrganizationContext}; fails outside one (STO-1). */
    default BlobStore current() {
        return store(OrganizationContext.current());
    }
}
