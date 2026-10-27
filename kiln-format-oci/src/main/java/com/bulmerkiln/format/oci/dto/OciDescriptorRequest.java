/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import org.jspecify.annotations.Nullable;

/** A descriptor in a pushed manifest or index, before validation. */
public record OciDescriptorRequest(
        @Nullable String mediaType,
        @Nullable String digest,
        @Nullable Long size,
        @Nullable String artifactType) {}
