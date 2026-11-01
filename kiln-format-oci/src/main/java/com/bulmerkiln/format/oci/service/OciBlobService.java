/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.store.OciRepositoryStore;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.RangeNotSatisfiableException;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.ByteRange;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Blob pulls, whole or by range (FMT-OCI-21). */
public final class OciBlobService {

    private final Database database;
    private final OciAccessService access;
    private final StorageRouter storage;

    public OciBlobService(Database database, OciAccessService access, StorageRouter storage) {
        this.database = database;
        this.access = access;
        this.storage = storage;
    }

    /**
     * Opens a blob the repository holds. A blob the organization holds but this repository doesn't
     * is unknown here, so names can't be used to read each other's content (FMT-OCI-21).
     */
    public BlobRead pull(OciRepositoryRequest request, Digest digest, @Nullable String range) throws IOException {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        boolean linked = database.transaction(tx -> OciRepositoryStore.find(tx, request.getRepository())
                .map(repository -> OciRepositoryStore.linked(tx, repository, digest))
                .orElse(false));
        if (!linked) {
            throw new OciException(OciErrorCode.BLOB_UNKNOWN, "Blob unknown");
        }
        access.checkRead(request, namespace, digest.toString());
        BlobStore store = storage.store(OrganizationContext.current());
        BlobInfo info =
                store.stat(digest).orElseThrow(() -> new OciException(OciErrorCode.BLOB_UNKNOWN, "Blob unknown"));
        try {
            ByteRange resolved = range == null
                    ? null
                    : ByteRange.parse(range, info.getSize()).orElse(null);
            return store.read(digest, resolved)
                    .orElseThrow(() -> new OciException(OciErrorCode.BLOB_UNKNOWN, "Blob unknown"));
        } catch (RangeNotSatisfiableException e) {
            throw new RefusedException(Refusal.of(Reason.RANGE_NOT_SATISFIABLE, "Range not satisfiable"));
        }
    }
}
