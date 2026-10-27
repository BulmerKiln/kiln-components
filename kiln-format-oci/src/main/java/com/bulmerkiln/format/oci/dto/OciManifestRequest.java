/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The fields of a pushed manifest or index that validation needs; everything else is kept only in the raw bytes. */
public record OciManifestRequest(
        @Nullable Integer schemaVersion,
        @Nullable String mediaType,
        @Nullable String artifactType,
        @Nullable OciDescriptorRequest config,
        @Nullable List<OciDescriptorRequest> layers,
        @Nullable List<OciDescriptorRequest> manifests,
        @Nullable OciDescriptorRequest subject) {}
