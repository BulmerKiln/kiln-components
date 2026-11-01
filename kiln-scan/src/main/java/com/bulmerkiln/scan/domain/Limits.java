/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import lombok.Value;

/**
 * Per-job limits that defeat archive bombs; a job that hits one reports {@code INCOMPLETE}, never
 * a clean pass (SCAN-7).
 */
@Value
public final class Limits {

    private final long maxUnpackedBytes;

    private final int maxFiles;

    private final int maxDepth;

    private final int timeoutSeconds;

    public Limits(long maxUnpackedBytes, int maxFiles, int maxDepth, int timeoutSeconds) {
        if (maxUnpackedBytes <= 0 || maxFiles <= 0 || maxDepth <= 0 || timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Limits must be positive");
        }
        this.maxUnpackedBytes = maxUnpackedBytes;
        this.maxFiles = maxFiles;
        this.maxDepth = maxDepth;
        this.timeoutSeconds = timeoutSeconds;
    }
}
