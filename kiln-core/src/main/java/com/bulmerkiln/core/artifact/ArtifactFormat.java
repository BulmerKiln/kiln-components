/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.artifact;

import java.util.regex.Pattern;

/**
 * A registry format's identifier, such as {@code oci} or {@code npm}, as stored in
 * {@code packages.format} and used as the {@code format} metric label. It's an open value, not an
 * enum: each format module defines its own, so adding a format never touches the kernel.
 */
public record ArtifactFormat(String id) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public ArtifactFormat {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid format ID: " + id);
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
