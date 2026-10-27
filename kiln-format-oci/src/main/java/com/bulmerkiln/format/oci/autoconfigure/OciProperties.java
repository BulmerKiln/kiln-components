/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.format.oci.domain.OciManifestParser;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The OCI format's settings, under {@code kiln.format.oci}.
 *
 * @param enabled whether the {@code /v2/} routes exist; on unless set to {@code false}
 * @param maxBlobSize the largest blob (layer) in bytes; 10 GiB unless set
 * @param maxManifestSize the largest manifest or index in bytes; 4 MiB unless set
 * @param tagPageSize the most tags one page lists; 1,000 unless set
 */
@ConfigurationProperties("kiln.format.oci")
public record OciProperties(
        @Nullable Boolean enabled,
        @Nullable Long maxBlobSize,
        @Nullable Integer maxManifestSize,
        @Nullable Integer tagPageSize) {

    public long maxBlobSizeOrDefault() {
        return maxBlobSize == null ? 10L * 1024 * 1024 * 1024 : maxBlobSize;
    }

    public int maxManifestSizeOrDefault() {
        return maxManifestSize == null ? OciManifestParser.DEFAULT_MAX_SIZE : maxManifestSize;
    }

    public int tagPageSizeOrDefault() {
        return tagPageSize == null ? 1000 : tagPageSize;
    }
}
