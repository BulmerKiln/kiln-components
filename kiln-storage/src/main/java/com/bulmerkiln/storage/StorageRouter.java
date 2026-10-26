/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.org.Org;

/**
 * Finds the store that holds an organization's blobs: one store for every organization in
 * kiln-oss, and a bucket or prefix per organization in kiln-cloud (STO-13).
 */
public interface StorageRouter {

    BlobStore store(Org org);
}
