/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** What a push stored: the manifest's digest, and its subject's when it has one. */
@Value
public final class OciManifestPushResponse {

    private final Digest digest;

    private final @Nullable Digest subject;
}
