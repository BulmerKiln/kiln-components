/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.domain;

import com.bulmerkiln.core.digest.Digest;
import java.util.Objects;
import lombok.Value;

@Value
public final class BlobInfo {

    private final Digest digest;

    private final long size;

    public BlobInfo(Digest digest, long size) {
        Objects.requireNonNull(digest, "digest");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        this.digest = digest;
        this.size = size;
    }
}
