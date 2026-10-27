/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciUploadProgressResponse;
import com.bulmerkiln.format.oci.dto.OciUploadResponse;
import com.bulmerkiln.format.oci.store.OciRepositoryStore;
import com.bulmerkiln.format.oci.store.OciUploadStore;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.store.BlobRecordStore;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Blob uploads: monolithic, chunked, single-request and mounted (FMT-OCI-22 to FMT-OCI-25). Bytes
 * stream straight to the store, which hashes them on the way.
 */
public final class OciUploadService {

    private final Database database;
    private final OciAccessService access;
    private final OciUploadStore store;

    public OciUploadService(Database database, OciAccessService access, OciUploadStore store) {
        this.database = database;
        this.access = access;
        this.store = store;
    }

    /** Starts an upload session (FMT-OCI-22). */
    public OciUploadProgressResponse start(OciRepositoryRequest request) throws IOException {
        Namespace namespace = access.requireToWrite(request, Operation.PUSH);
        UploadId upload = store.create();
        database.run(tx -> {
            UUID repository = OciRepositoryStore.ensure(tx, request.repository(), namespace);
            OciRepositoryStore.startUpload(tx, repository, upload.value());
        });
        return progress(request, upload, 0);
    }

    /** Uploads a whole blob in one request, {@code POST ?digest=} (FMT-OCI-22). */
    public OciUploadResponse upload(OciRepositoryRequest request, Digest digest, InputStream body) throws IOException {
        UploadId upload = new UploadId(start(request).session());
        store.append(upload, 0L, body);
        return finish(request, upload, digest);
    }

    /**
     * Mounts a blob from another repository of the organization. Only when the caller may pull from
     * there and the blob is linked there; otherwise it's a plain upload, which reveals nothing
     * about the other repository (FMT-OCI-23).
     */
    public Optional<OciUploadResponse> mount(OciRepositoryRequest request, OciRepositoryRequest from, Digest digest) {
        Namespace namespace = access.requireToWrite(request, Operation.PUSH);
        try {
            access.require(from, Operation.PULL);
        } catch (RefusedException e) {
            return Optional.empty();
        }
        boolean mounted = database.transaction(tx -> {
            Optional<UUID> source = OciRepositoryStore.find(tx, from.repository());
            if (source.isEmpty() || !OciRepositoryStore.linked(tx, source.get(), digest)) {
                return false;
            }
            UUID repository = OciRepositoryStore.ensure(tx, request.repository(), namespace);
            OciRepositoryStore.link(tx, repository, digest);
            return true;
        });
        return mounted ? Optional.of(stored(request, digest)) : Optional.empty();
    }

    /** Appends a chunk at {@code offset}, or at the end when the client gave none (FMT-OCI-24). */
    public OciUploadProgressResponse patch(
            OciRepositoryRequest request, String session, @Nullable Long offset, InputStream body) throws IOException {
        UploadId upload = session(request, session);
        return progress(request, upload, store.append(upload, offset, body));
    }

    public OciUploadProgressResponse status(OciRepositoryRequest request, String session) throws IOException {
        UploadId upload = session(request, session);
        return progress(request, upload, store.size(upload));
    }

    /** Appends any final chunk, verifies the digest, and makes the blob part of the repository. */
    public OciUploadResponse finish(
            OciRepositoryRequest request, String session, Digest digest, @Nullable InputStream body)
            throws IOException {
        UploadId upload = session(request, session);
        if (body != null) {
            store.append(upload, null, body);
        }
        return finish(request, upload, digest);
    }

    public void cancel(OciRepositoryRequest request, String session) throws IOException {
        UploadId upload = session(request, session);
        store.abort(upload);
        endUpload(upload);
    }

    private OciUploadResponse finish(OciRepositoryRequest request, UploadId upload, Digest digest) throws IOException {
        BlobInfo blob;
        try {
            blob = store.commit(upload, digest);
        } catch (OciException e) {
            endUpload(upload);
            throw e;
        }
        database.run(tx -> {
            UUID repository = OciRepositoryStore.find(tx, request.repository()).orElseThrow();
            BlobRecordStore.record(tx, blob.digest(), blob.size());
            OciRepositoryStore.link(tx, repository, blob.digest());
            OciRepositoryStore.endUpload(tx, upload.value());
        });
        return stored(request, blob.digest());
    }

    /** The session, if it was started through this repository; anyone else's is unknown here. */
    private UploadId session(OciRepositoryRequest request, String session) {
        access.requireToWrite(request, Operation.PUSH);
        UploadId upload = OciUploadStore.sessionId(session);
        boolean belongs = database.transaction(tx -> OciRepositoryStore.find(tx, request.repository())
                .map(repository -> OciRepositoryStore.uploadBelongs(tx, repository, upload.value()))
                .orElse(false));
        if (!belongs) {
            throw OciUploadStore.unknown();
        }
        return upload;
    }

    private void endUpload(UploadId upload) {
        database.run(tx -> OciRepositoryStore.endUpload(tx, upload.value()));
    }

    private static OciUploadProgressResponse progress(OciRepositoryRequest request, UploadId upload, long size) {
        return new OciUploadProgressResponse(request.url("blobs/uploads/" + upload), upload.value(), size);
    }

    private static OciUploadResponse stored(OciRepositoryRequest request, Digest digest) {
        return new OciUploadResponse(request.url("blobs/" + digest), digest);
    }
}
