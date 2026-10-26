/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import org.jspecify.annotations.Nullable;

/**
 * Makes tool-reported text fit the protocol: control characters removed and the length bounded.
 * One multi-line title from a tool must not fail a whole scan.
 */
public final class ToolText {

    private ToolText() {}

    /** The cleaned text, or null if nothing printable is left. */
    public static @Nullable String clean(@Nullable String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.codePoints()
                .map(c -> Character.isISOControl(c) ? ' ' : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll(" {2,}", " ")
                .strip();
        if (cleaned.length() > max) {
            cleaned = cleaned.substring(0, max).strip();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}
