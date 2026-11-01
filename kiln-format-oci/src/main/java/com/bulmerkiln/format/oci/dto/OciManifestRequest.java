/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** The fields of a pushed manifest or index that validation needs; everything else is kept only in the raw bytes. */
@Value
public final class OciManifestRequest {

    private final @Nullable Integer schemaVersion;

    private final @Nullable String mediaType;

    private final @Nullable String artifactType;

    private final @Nullable OciDescriptorRequest config;

    private final @Nullable List<OciDescriptorRequest> layers;

    private final @Nullable List<OciDescriptorRequest> manifests;

    private final @Nullable OciDescriptorRequest subject;
}
