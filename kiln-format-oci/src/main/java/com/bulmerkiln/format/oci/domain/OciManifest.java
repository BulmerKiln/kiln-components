/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A validated manifest or index, and the references the registry must check before storing it.
 */
@Value
public final class OciManifest {

    /** The SHA-256 of the raw bytes, which the stored bytes keep exactly. */
    private final Digest digest;

    private final String mediaType;

    private final @Nullable String artifactType;

    /** The config and layers of an image manifest; empty for an index. */
    private final List<OciDescriptor> blobs;

    /** The child manifests of an index; empty for an image manifest. */
    private final List<OciDescriptor> manifests;

    /** The manifest this one refers to (OCI 1.1 referrers), which may not exist yet. */
    private final @Nullable OciDescriptor subject;

    public boolean isIndex() {
        return OciMediaTypes.INDEXES.contains(mediaType);
    }
}
