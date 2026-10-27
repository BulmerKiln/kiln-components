/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.util;

import tools.jackson.databind.json.JsonMapper;

/** Writes error bodies as JSON, escaping every value, so formats never build JSON by hand. */
public final class FormatJsonUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private FormatJsonUtils() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
