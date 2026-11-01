/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** A content descriptor (image-spec, descriptor.md): what a manifest points at. */
@Value
public final class OciDescriptor {

    private final String mediaType;

    private final Digest digest;

    private final long size;

    private final @Nullable String artifactType;
}
