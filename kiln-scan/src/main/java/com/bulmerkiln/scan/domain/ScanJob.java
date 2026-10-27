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

/**
 * A job for the worker: scan one digest of one organization.
 *
 * @param blobUrl a short-lived presigned URL; the worker has no other access to storage
 */
public record ScanJob(
        int protocol,
        UUID jobId,
        UUID organizationId,
        Digest digest,
        String mediaType,
        URI blobUrl,
        Instant blobUrlExpiresAt,
        List<ScannerId> scanners,
        Limits limits) {

    public ScanJob {
        ScanProtocol.requireVersion(protocol);
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(blobUrl, "blobUrl");
        Objects.requireNonNull(blobUrlExpiresAt, "blobUrlExpiresAt");
        Objects.requireNonNull(limits, "limits");
        BoundsUtils.text(mediaType, "mediaType", 255);
        scanners = List.copyOf(scanners);
        if (scanners.isEmpty()) {
            throw new IllegalArgumentException("A job runs at least one scanner");
        }
    }
}
