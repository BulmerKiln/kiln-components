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
import com.bulmerkiln.format.oci.domain.OciManifestRecord;
import com.bulmerkiln.format.oci.dto.OciIndexResponse;
import com.bulmerkiln.format.oci.dto.OciReferrersResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciTagListResponse;
import com.bulmerkiln.format.oci.dto.OciTagPageResponse;
import com.bulmerkiln.format.util.FormatJsonUtils;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Tag listing and the referrers API (FMT-OCI-26, FMT-OCI-27). */
public final class OciTagService {

    private final Database database;
    private final OciAccessService access;
    private final OciRepositoryService repositories;
    private final OciManifestRecordService manifests;
    private final int maxPageSize;

    public OciTagService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests,
            int maxPageSize) {
        this.database = database;
        this.access = access;
        this.repositories = repositories;
        this.manifests = manifests;
        this.maxPageSize = maxPageSize;
    }

    /** Tags after {@code last}, at most {@code n} of them (at most the configured page size). */
    public OciTagPageResponse tags(OciRepositoryRequest request, @Nullable Integer n, @Nullable String last) {
        access.require(request, Operation.PULL);
        int size = n == null ? maxPageSize : Math.clamp(n, 0, maxPageSize);
        List<String> tags = database.transaction(() -> {
            UUID repository = repositories
                    .find(request.getRepository())
                    .orElseThrow(() -> new OciException(OciErrorCode.NAME_UNKNOWN, "Repository unknown"));
            return manifests.tags(repository, last, size + 1);
        });
        boolean more = tags.size() > size;
        List<String> page = more ? tags.subList(0, size) : tags;
        String body = FormatJsonUtils.write(new OciTagListResponse(request.getAddressed(), page));
        String next = more ? request.path("tags/list?n=" + size + "&last=" + page.getLast()) : null;
        return new OciTagPageResponse(body, next);
    }

    /** An index of the manifests whose subject is {@code digest}; an unknown digest has none. */
    public OciReferrersResponse referrers(OciRepositoryRequest request, Digest digest, @Nullable String artifactType) {
        access.require(request, Operation.PULL);
        List<OciManifestRecord> referrers = database.transaction(() -> repositories
                .find(request.getRepository())
                .map(repository -> manifests.referrers(repository, digest, artifactType))
                .orElse(List.of()));
        List<OciIndexResponse.Entry> entries = referrers.stream()
                .map(m -> new OciIndexResponse.Entry(
                        m.getMediaType(), m.getDigest().toString(), m.getSize(), m.getArtifactType()))
                .toList();
        return new OciReferrersResponse(
                FormatJsonUtils.write(new OciIndexResponse(2, OciMediaTypes.OCI_INDEX, entries)), artifactType != null);
    }
}
