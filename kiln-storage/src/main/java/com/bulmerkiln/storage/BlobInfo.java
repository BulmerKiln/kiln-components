/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.digest.Digest;
import java.util.Objects;

public record BlobInfo(Digest digest, long size) {

    public BlobInfo {
        Objects.requireNonNull(digest, "digest");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
