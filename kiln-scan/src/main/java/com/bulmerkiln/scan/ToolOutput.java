/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parses a tool's JSON report strictly; Jackson's default nesting and size limits apply. */
public final class ToolOutput {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private ToolOutput() {}

    /** The report, or an exception for anything that isn't one JSON value, which fails the scan. */
    public static JsonNode json(String output) {
        JsonNode node = MAPPER.readTree(output);
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("The tool produced no report");
        }
        return node;
    }
}
