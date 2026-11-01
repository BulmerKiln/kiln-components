/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import java.util.UUID;

/**
 * Deleting a tag, or a manifest with the tags pointing at it (FMT-OCI-20). A manifest an index
 * still references is refused, so an index never points at nothing.
 */
public final class OciDeleteService {

    private final Database database;
    private final OciAccessService access;
    private final OciRepositoryService repositories;
    private final OciManifestRecordService manifests;

    public OciDeleteService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests) {
        this.database = database;
        this.access = access;
        this.repositories = repositories;
        this.manifests = manifests;
    }

    public void delete(OciRepositoryRequest request, OciReference reference) {
        access.require(request, Operation.DELETE);
        boolean deleted = database.transaction(() -> {
            UUID repository = repositories.find(request.getRepository()).orElse(null);
            if (repository == null) {
                return false;
            }
            return switch (reference) {
                case OciReference.Tag tag -> manifests.deleteTag(repository, tag.getValue());
                case OciReference.ByDigest byDigest -> {
                    if (manifests.referencedByIndex(repository, byDigest.getDigest())) {
                        throw new RefusedException(Refusal.of(
                                Reason.UNSUPPORTED, "An index references this manifest; delete the index first"));
                    }
                    yield manifests.delete(repository, byDigest.getDigest());
                }
            };
        });
        if (!deleted) {
            throw new OciException(OciErrorCode.MANIFEST_UNKNOWN, "Manifest unknown");
        }
    }
}
