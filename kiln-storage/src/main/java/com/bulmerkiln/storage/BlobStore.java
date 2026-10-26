/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.digest.Digest;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The narrow storage contract every format writes through (STO-1). Blobs are immutable and
 * content-addressed. Uploads are resumable sessions whose bytes are verified against the expected
 * digest before they become a blob.
 *
 * <p>Every method acts on the organization in {@link com.bulmerkiln.core.org.OrgContext}, and
 * fails outside one. Blobs are never shared between organizations.
 */
public interface BlobStore {

    /** Starts an empty upload session. */
    UploadId createUpload() throws IOException;

    /**
     * The number of bytes received so far, the offset the next chunk must start at.
     *
     * @throws UploadNotFoundException if the session doesn't exist
     */
    long uploadSize(UploadId upload) throws IOException;

    /**
     * Appends a chunk. A chunk is applied entirely or not at all: if reading {@code data} fails, or
     * the upload would exceed {@code maxSize}, the session is left as it was.
     *
     * @param offset where the chunk starts, which must equal {@link #uploadSize}
     * @return the new upload size
     * @throws UploadNotFoundException if the session doesn't exist
     * @throws UploadOffsetException if {@code offset} isn't the current size (a gap or an overlap)
     * @throws BlobTooLargeException if the upload would exceed {@code maxSize}
     */
    long append(UploadId upload, long offset, InputStream data, long maxSize) throws IOException;

    /**
     * Verifies the upload against {@code expected} and moves it into place atomically. Committing
     * content that's already stored succeeds and keeps the existing blob.
     *
     * @throws UploadNotFoundException if the session doesn't exist
     * @throws DigestMismatchException if the content doesn't match; the session is deleted
     */
    BlobInfo commit(UploadId upload, Digest expected) throws IOException;

    /** Deletes an upload session. Does nothing if it doesn't exist. */
    void abortUpload(UploadId upload) throws IOException;

    Optional<BlobInfo> stat(Digest digest) throws IOException;

    /**
     * Opens a blob for reading, optionally only part of it.
     *
     * @param range the bytes to read, already resolved against the blob size, or {@code null} for
     *     all of it
     * @throws RangeNotSatisfiableException if {@code range} ends beyond the blob
     */
    Optional<BlobRead> read(Digest digest, @Nullable ByteRange range) throws IOException;

    /** Deletes a blob, returning whether it existed. Only GC calls this. */
    boolean delete(Digest digest) throws IOException;

    /** Every stored blob, for the GC sweep. The stream must be closed. */
    @MustBeClosed
    Stream<Digest> list() throws IOException;
}
