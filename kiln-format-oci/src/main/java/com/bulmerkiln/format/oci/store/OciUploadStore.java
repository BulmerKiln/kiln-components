/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.store;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.BlobTooLargeException;
import com.bulmerkiln.storage.DigestMismatchException;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.UploadNotFoundException;
import com.bulmerkiln.storage.UploadOffsetException;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;

/**
 * The organization's upload sessions in the blob store, with the store's failures answered in the
 * spec's terms: a gap or overlap is {@code 416}, too large {@code 413}, a missing session {@code
 * BLOB_UPLOAD_UNKNOWN}, the wrong content {@code DIGEST_INVALID} (FMT-OCI-24, FMT-OCI-25).
 */
public final class OciUploadStore {

    private final StorageRouter storage;
    private final long maxBlobSize;

    public OciUploadStore(StorageRouter storage, long maxBlobSize) {
        this.storage = storage;
        this.maxBlobSize = maxBlobSize;
    }

    public UploadId create() throws IOException {
        return store().createUpload();
    }

    public long size(UploadId upload) throws IOException {
        try {
            return store().uploadSize(upload);
        } catch (UploadNotFoundException e) {
            throw unknown();
        }
    }

    /** Appends a chunk at {@code offset}, or at the end when it's null, and returns the new size. */
    public long append(UploadId upload, @Nullable Long offset, InputStream body) throws IOException {
        try {
            long at = offset == null ? store().uploadSize(upload) : offset;
            return store().append(upload, at, body, maxBlobSize);
        } catch (UploadOffsetException e) {
            throw new RefusedException(
                    Refusal.of(Reason.RANGE_NOT_SATISFIABLE, "The chunk doesn't start where the upload ends"));
        } catch (BlobTooLargeException e) {
            throw new RefusedException(Refusal.of(Reason.TOO_LARGE, "Blobs are limited to " + maxBlobSize + " bytes"));
        } catch (UploadNotFoundException e) {
            throw unknown();
        }
    }

    /** Verifies the content and moves it into place; the wrong content ends the session. */
    public BlobInfo commit(UploadId upload, Digest digest) throws IOException {
        try {
            return store().commit(upload, digest);
        } catch (DigestMismatchException e) {
            throw new OciException(OciErrorCode.DIGEST_INVALID, "The content doesn't match its digest");
        } catch (UploadNotFoundException e) {
            throw unknown();
        }
    }

    public void abort(UploadId upload) throws IOException {
        store().abortUpload(upload);
    }

    /** A session ID as given in the URL; anything but a canonical UUID is unknown. */
    public static UploadId sessionId(String session) {
        try {
            return UploadId.parse(session);
        } catch (UploadNotFoundException e) {
            throw unknown();
        }
    }

    public static OciException unknown() {
        return new OciException(OciErrorCode.BLOB_UPLOAD_UNKNOWN, "Upload unknown");
    }

    private BlobStore store() {
        return storage.store(OrganizationContext.current());
    }
}
