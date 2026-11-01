/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.registry.mapper.PackageMapper;
import com.bulmerkiln.registry.repository.BlobRepository;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The organization's record of the blobs it holds, whichever format stored them. Recording the same
 * blob twice, as two concurrent pushes do, is harmless (REG-2).
 */
public final class BlobRecordService {

    private final BlobRepository blobs;
    private final PackageMapper mapper;

    public BlobRecordService(BlobRepository blobs, PackageMapper mapper) {
        this.blobs = blobs;
        this.mapper = mapper;
    }

    /** Records a blob the store now holds, with its SHA-256 when that's its digest. */
    public void record(Digest digest, long size) {
        record(digest, size, BlobChecksums.NONE);
    }

    /**
     * Records a blob with the checksums taken while it streamed in, or marks one already recorded
     * as referenced again, adding any checksums it lacked.
     */
    public void record(Digest digest, long size, BlobChecksums checksums) {
        @Nullable String sha256 = checksums.getSha256();
        if (sha256 == null && digest.getAlgorithm() == DigestAlgorithm.SHA256) {
            sha256 = digest.getEncoded();
        }
        blobs.record(
                organization(),
                digest.toString(),
                size,
                checksums.getMd5(),
                checksums.getSha1(),
                sha256,
                checksums.getSha512());
    }

    /** The size of a blob the organization holds, or empty if it holds none with that digest. */
    public Optional<Long> size(Digest digest) {
        return blobs.findOne(organization(), digest.toString()).map(blob -> blob.getSize());
    }

    /** The checksums recorded for a blob, or empty if the organization holds none with that digest. */
    public Optional<BlobChecksums> checksums(Digest digest) {
        return blobs.findOne(organization(), digest.toString()).map(mapper::toChecksums);
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
