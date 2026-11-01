/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.entity;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** A stored manifest row: its digest, media and artifact type, and the bytes exactly as pushed. */
@Value
public final class OciManifestEntity {

    private final Digest digest;

    private final String mediaType;

    private final @Nullable String artifactType;

    private final byte[] content;
}
