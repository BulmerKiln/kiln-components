/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** An image index, as the referrers API answers. */
@Value
public final class OciIndexResponse {

    private final int schemaVersion;

    private final String mediaType;

    private final List<Entry> manifests;

    /** A manifest in the index. */
    @Value
    public static final class Entry {

        private final String mediaType;

        private final String digest;

        private final long size;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final @Nullable String artifactType;
    }
}
