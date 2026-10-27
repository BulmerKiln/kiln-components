/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.dto;

import java.util.Map;

/**
 * A format's rendering of a refusal: its error body and any headers of its own. The status, the
 * challenge and the security headers are added by {@link FormatResponse#refusal}, not by formats.
 */
public record RefusalResponse(String contentType, String body, Map<String, String> headers) {

    public RefusalResponse {
        headers = Map.copyOf(headers);
    }

    public static RefusalResponse of(String contentType, String body) {
        return new RefusalResponse(contentType, body, Map.of());
    }
}
