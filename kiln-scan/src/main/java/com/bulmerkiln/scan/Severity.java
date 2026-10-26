/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

/** Finding severity, from most to least severe. */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    /** The more severe of the two. */
    public Severity max(Severity other) {
        return compareTo(other) <= 0 ? this : other;
    }
}
