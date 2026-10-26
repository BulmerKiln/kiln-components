/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

/**
 * Per-job limits that defeat archive bombs; a job that hits one reports {@code INCOMPLETE}, never
 * a clean pass (SCAN-7).
 */
public record Limits(long maxUnpackedBytes, int maxFiles, int maxDepth, int timeoutSeconds) {

    public Limits {
        if (maxUnpackedBytes <= 0 || maxFiles <= 0 || maxDepth <= 0 || timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Limits must be positive");
        }
    }
}
