/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.regex.Pattern;

/** Field checks for untrusted messages. */
final class Bounds {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    private Bounds() {}

    static void identifier(String value, String field) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid identifier");
        }
    }

    /** Non-blank, bounded and free of control characters, so it's safe to log and display. */
    static void text(String value, String field, int max) {
        if (value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be 1 to " + max + " printable characters");
        }
    }
}
