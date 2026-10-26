/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.core.digest.Digest;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A validated manifest or index, and the references the registry must check before storing it.
 *
 * @param digest the SHA-256 of the raw bytes, which the stored bytes keep exactly
 * @param blobs the config and layers of an image manifest; empty for an index
 * @param manifests the child manifests of an index; empty for an image manifest
 * @param subject the manifest this one refers to (OCI 1.1 referrers), which may not exist yet
 */
public record Manifest(
        Digest digest,
        String mediaType,
        @Nullable String artifactType,
        List<Descriptor> blobs,
        List<Descriptor> manifests,
        @Nullable Descriptor subject) {

    public boolean isIndex() {
        return MediaTypes.INDEXES.contains(mediaType);
    }
}
