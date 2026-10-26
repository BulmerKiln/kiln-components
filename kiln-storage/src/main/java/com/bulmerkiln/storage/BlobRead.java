/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/** An open blob, or part of one, ready to be sent to a client. */
public interface BlobRead extends Closeable {

    /** The size of the whole blob. */
    long blobSize();

    /** Where the bytes being read start within the blob. */
    long offset();

    /** How many bytes are being read. */
    long length();

    /**
     * Writes the bytes to {@code target}. Filesystem stores use zero-copy transfer, so the bytes
     * never pass through the heap.
     */
    void transferTo(WritableByteChannel target) throws IOException;
}
