/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An image index, as the referrers API answers. */
public record OciIndexResponse(int schemaVersion, String mediaType, List<Entry> manifests) {

    /** A manifest in the index. */
    public record Entry(
            String mediaType,
            String digest,
            long size,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable
            String artifactType) {}
}
