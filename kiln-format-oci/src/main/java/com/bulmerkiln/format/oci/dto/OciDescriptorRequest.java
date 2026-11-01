/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import lombok.Value;
import org.jspecify.annotations.Nullable;

/** A descriptor in a pushed manifest or index, before validation. */
@Value
public final class OciDescriptorRequest {

    private final @Nullable String mediaType;

    private final @Nullable String digest;

    private final @Nullable Long size;

    private final @Nullable String artifactType;
}
