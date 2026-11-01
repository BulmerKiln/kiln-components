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
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.service.BlobRecordService;
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
    private final OciRepositoryService repositories;
    private final BlobRecordService blobs;
    private final OciUploadStorageService storage;

    public OciUploadService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            BlobRecordService blobs,
            OciUploadStorageService storage) {
        this.database = database;
        this.access = access;
        this.repositories = repositories;
        this.blobs = blobs;
        this.storage = storage;
    }

    /** Starts an upload session (FMT-OCI-22). */
    public OciUploadProgressResponse start(OciRepositoryRequest request) throws IOException {
        Namespace namespace = access.requireToWrite(request, Operation.PUSH);
        UploadId upload = storage.create();
        database.run(() -> {
            UUID repository = repositories.ensure(request.getRepository(), namespace);
            repositories.startUpload(repository, upload.getValue());
        });
        return progress(request, upload, 0);
    }

    /** Uploads a whole blob in one request, {@code POST ?digest=} (FMT-OCI-22). */
    public OciUploadResponse upload(OciRepositoryRequest request, Digest digest, InputStream body) throws IOException {
        UploadId upload = new UploadId(start(request).getSession());
        storage.append(upload, 0L, body);
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
        boolean mounted = database.transaction(() -> {
            Optional<UUID> source = repositories.find(from.getRepository());
            if (source.isEmpty() || !repositories.linked(source.get(), digest)) {
                return false;
            }
            UUID repository = repositories.ensure(request.getRepository(), namespace);
            repositories.link(repository, digest);
            return true;
        });
        return mounted ? Optional.of(stored(request, digest)) : Optional.empty();
    }

    /** Appends a chunk at {@code offset}, or at the end when the client gave none (FMT-OCI-24). */
    public OciUploadProgressResponse patch(
            OciRepositoryRequest request, String session, @Nullable Long offset, InputStream body) throws IOException {
        UploadId upload = session(request, session);
        return progress(request, upload, storage.append(upload, offset, body));
    }

    public OciUploadProgressResponse status(OciRepositoryRequest request, String session) throws IOException {
        UploadId upload = session(request, session);
        return progress(request, upload, storage.size(upload));
    }

    /** Appends any final chunk, verifies the digest, and makes the blob part of the repository. */
    public OciUploadResponse finish(
            OciRepositoryRequest request, String session, Digest digest, @Nullable InputStream body)
            throws IOException {
        UploadId upload = session(request, session);
        if (body != null) {
            storage.append(upload, null, body);
        }
        return finish(request, upload, digest);
    }

    public void cancel(OciRepositoryRequest request, String session) throws IOException {
        UploadId upload = session(request, session);
        storage.abort(upload);
        endUpload(upload);
    }

    private OciUploadResponse finish(OciRepositoryRequest request, UploadId upload, Digest digest) throws IOException {
        BlobInfo blob;
        try {
            blob = storage.commit(upload, digest);
        } catch (OciException e) {
            endUpload(upload);
            throw e;
        }
        database.run(() -> {
            UUID repository = repositories.find(request.getRepository()).orElseThrow();
            blobs.record(blob.getDigest(), blob.getSize());
            repositories.link(repository, blob.getDigest());
            repositories.endUpload(upload.getValue());
        });
        return stored(request, blob.getDigest());
    }

    /** The session, if it was started through this repository; anyone else's is unknown here. */
    private UploadId session(OciRepositoryRequest request, String session) {
        access.requireToWrite(request, Operation.PUSH);
        UploadId upload = OciUploadStorageService.sessionId(session);
        boolean belongs = database.transaction(() -> repositories
                .find(request.getRepository())
                .map(repository -> repositories.uploadBelongs(repository, upload.getValue()))
                .orElse(false));
        if (!belongs) {
            throw OciUploadStorageService.unknown();
        }
        return upload;
    }

    private void endUpload(UploadId upload) {
        database.run(() -> repositories.endUpload(upload.getValue()));
    }

    private static OciUploadProgressResponse progress(OciRepositoryRequest request, UploadId upload, long size) {
        return new OciUploadProgressResponse(request.path("blobs/uploads/" + upload), upload.getValue(), size);
    }

    private static OciUploadResponse stored(OciRepositoryRequest request, Digest digest) {
        return new OciUploadResponse(request.path("blobs/" + digest), digest);
    }
}
