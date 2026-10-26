/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** An upload session. It appears in client-visible URLs, so parsing is strict. */
public record UploadId(UUID value) {

    /** The canonical lowercase form only; {@link UUID#fromString} accepts much more. */
    private static final Pattern CANONICAL =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public UploadId {
        Objects.requireNonNull(value, "value");
    }

    /** A new random (version 4) session ID. */
    public static UploadId random() {
        return new UploadId(UUID.randomUUID());
    }

    /** Parses a canonical UUID, or throws {@link UploadNotFoundException} for anything else. */
    public static UploadId parse(String value) {
        if (value.length() != 36 || !CANONICAL.matcher(value).matches()) {
            throw new UploadNotFoundException();
        }
        return new UploadId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
