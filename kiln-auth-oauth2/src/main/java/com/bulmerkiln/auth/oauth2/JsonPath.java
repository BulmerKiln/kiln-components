/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A dotted path into a parsed JSON object, such as {@code data.user.id}. Only object keys, no
 * array indexes or wildcards, so a path names exactly one value.
 */
public record JsonPath(List<String> keys) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_DEPTH = 8;

    public JsonPath {
        keys = List.copyOf(keys);
        if (keys.isEmpty() || keys.size() > MAX_DEPTH) {
            throw new IllegalArgumentException("A JSON path has 1 to " + MAX_DEPTH + " keys");
        }
        for (String key : keys) {
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid JSON path key: " + key);
            }
        }
    }

    public static JsonPath parse(String path) {
        return new JsonPath(List.of(path.split("\\.", -1)));
    }

    /** The value at this path, or null if any step is missing or isn't an object. */
    public @Nullable Object read(Map<?, ?> json) {
        Object current = json;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> object)) {
                return null;
            }
            current = object.get(key);
        }
        return current;
    }

    @Override
    public String toString() {
        return String.join(".", keys);
    }
}
