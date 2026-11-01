/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.scan.ScanProtocol;
import com.bulmerkiln.scan.util.BoundsUtils;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;

/**
 * A job for the worker: scan one digest of one organization.
 */
@Value
public final class ScanJob {

    private final int protocol;

    private final UUID jobId;

    private final UUID organizationId;

    private final Digest digest;

    private final String mediaType;

    /** A short-lived presigned URL; the worker has no other access to storage. */
    private final URI blobUrl;

    private final Instant blobUrlExpiresAt;

    private final List<ScannerId> scanners;

    private final Limits limits;

    public ScanJob(
            int protocol,
            UUID jobId,
            UUID organizationId,
            Digest digest,
            String mediaType,
            URI blobUrl,
            Instant blobUrlExpiresAt,
            List<ScannerId> scanners,
            Limits limits) {
        ScanProtocol.requireVersion(protocol);
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(blobUrl, "blobUrl");
        Objects.requireNonNull(blobUrlExpiresAt, "blobUrlExpiresAt");
        Objects.requireNonNull(limits, "limits");
        BoundsUtils.text(mediaType, "mediaType", 255);
        this.scanners = List.copyOf(scanners);
        if (this.scanners.isEmpty()) {
            throw new IllegalArgumentException("A job runs at least one scanner");
        }
        this.protocol = protocol;
        this.jobId = jobId;
        this.organizationId = organizationId;
        this.digest = digest;
        this.mediaType = mediaType;
        this.blobUrl = blobUrl;
        this.blobUrlExpiresAt = blobUrlExpiresAt;
        this.limits = limits;
    }
}
