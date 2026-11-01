/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A blob's checksums as lowercase hex, each taken while its bytes streamed in, or null where no one
 * took it. They answer clients that ask for a checksum file; a blob is only ever found by digest.
 */
@Value
public final class BlobChecksums {

    public static final BlobChecksums NONE = new BlobChecksums(null, null, null, null);

    private final @Nullable String md5;

    private final @Nullable String sha1;

    private final @Nullable String sha256;

    private final @Nullable String sha512;
}
