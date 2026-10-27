/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import com.bulmerkiln.core.digest.Digest;
import org.jspecify.annotations.Nullable;

/** A stored manifest row: its digest, media and artifact type, and the bytes exactly as pushed. */
@SuppressWarnings("ArrayRecordComponent")
public record OciManifestEntity(
        Digest digest, String mediaType, @Nullable String artifactType, byte[] content) {}
