/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import org.jspecify.annotations.Nullable;

/** A content descriptor (image-spec, descriptor.md): what a manifest points at. */
public record OciDescriptor(
        String mediaType,
        Digest digest,
        long size,
        @Nullable String artifactType) {}
