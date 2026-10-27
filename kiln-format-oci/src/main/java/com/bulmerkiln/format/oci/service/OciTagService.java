/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.format.oci.dto.OciIndexResponse;
import com.bulmerkiln.format.oci.dto.OciReferrersResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciTagListResponse;
import com.bulmerkiln.format.oci.dto.OciTagPageResponse;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import com.bulmerkiln.format.oci.store.OciManifestStore;
import com.bulmerkiln.format.oci.store.OciRepositoryStore;
import com.bulmerkiln.format.util.FormatJsonUtils;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Tag listing and the referrers API (FMT-OCI-26, FMT-OCI-27). */
public final class OciTagService {

    private final Database database;
    private final OciAccessService access;
    private final int maxPageSize;

    public OciTagService(Database database, OciAccessService access, int maxPageSize) {
        this.database = database;
        this.access = access;
        this.maxPageSize = maxPageSize;
    }

    /** Tags after {@code last}, at most {@code n} of them (at most the configured page size). */
    public OciTagPageResponse tags(OciRepositoryRequest request, @Nullable Integer n, @Nullable String last) {
        access.require(request, Operation.PULL);
        int size = n == null ? maxPageSize : Math.clamp(n, 0, maxPageSize);
        List<String> tags = database.transaction(tx -> {
            UUID repository = OciRepositoryStore.find(tx, request.repository())
                    .orElseThrow(() -> new OciException(OciErrorCode.NAME_UNKNOWN, "Repository unknown"));
            return OciManifestStore.tags(tx, repository, last, size + 1);
        });
        boolean more = tags.size() > size;
        List<String> page = more ? tags.subList(0, size) : tags;
        String body = FormatJsonUtils.write(new OciTagListResponse(request.addressed(), page));
        String next = more ? request.path("tags/list?n=" + size + "&last=" + page.getLast()) : null;
        return new OciTagPageResponse(body, next);
    }

    /** An index of the manifests whose subject is {@code digest}; an unknown digest has none. */
    public OciReferrersResponse referrers(OciRepositoryRequest request, Digest digest, @Nullable String artifactType) {
        access.require(request, Operation.PULL);
        List<OciManifestEntity> referrers = database.transaction(tx -> OciRepositoryStore.find(tx, request.repository())
                .map(repository -> OciManifestStore.referrers(tx, repository, digest, artifactType))
                .orElse(List.of()));
        List<OciIndexResponse.Entry> entries = referrers.stream()
                .map(m -> new OciIndexResponse.Entry(
                        m.mediaType(), m.digest().toString(), m.content().length, m.artifactType()))
                .toList();
        return new OciReferrersResponse(
                FormatJsonUtils.write(new OciIndexResponse(2, OciMediaTypes.OCI_INDEX, entries)), artifactType != null);
    }
}
