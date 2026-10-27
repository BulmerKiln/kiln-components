/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.core.digest.Digest;
import org.jspecify.annotations.Nullable;

/** What a push stored: the manifest's digest, and its subject's when it has one. */
public record OciManifestPushResponse(
        Digest digest, @Nullable Digest subject) {}
