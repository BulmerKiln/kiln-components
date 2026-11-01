/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.format.oci.domain.OciManifestParser;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The OCI format's settings, under {@code kiln.format.oci}.
 */
@ConfigurationProperties("kiln.format.oci")
@Value
public final class OciProperties {

    /** Whether the {@code /v2/} routes exist; on unless set to {@code false}. */
    private final @Nullable Boolean enabled;

    /** The largest blob (layer) in bytes; 10 GiB unless set. */
    private final @Nullable Long maxBlobSize;

    /** The largest manifest or index in bytes; 4 MiB unless set. */
    private final @Nullable Integer maxManifestSize;

    /** The most tags one page lists; 1,000 unless set. */
    private final @Nullable Integer tagPageSize;

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
