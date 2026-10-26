/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.Map;

/**
 * A format's rendering of a refusal: its error body and any headers of its own. The status, the
 * challenge and the security headers are added by {@link FormatResponse#refusal}, not by formats.
 */
public record RefusalBody(String contentType, String body, Map<String, String> headers) {

    public RefusalBody {
        headers = Map.copyOf(headers);
    }

    public static RefusalBody of(String contentType, String body) {
        return new RefusalBody(contentType, body, Map.of());
    }
}
