/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

/** What a format request does to an artifact, for the {@link ArtifactAccess} check. */
public enum Operation {
    PULL,
    /** Push or publish. */
    PUSH,
    DELETE
}
