/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.io.IOException;

/**
 * A storage backend a module offers, such as the filesystem or S3. Several can be on the
 * classpath at once; {@code kiln.storage.type} names the one to use.
 */
public interface StorageBackend {

    /** The value of {@code kiln.storage.type} that selects this backend, such as {@code fs}. */
    String type();

    /** Creates the store, validating this backend's own settings. Called only when it's selected. */
    BlobStore create() throws IOException;
}
