/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** A complete response to send, with every header value checked for injection. */
public record FormatResponse(int status, String contentType, String body, Map<String, String> headers) {

    /** RFC 9110 field names. */
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+");

    public FormatResponse {
        requireHeaderValue(contentType);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!HEADER_NAME.matcher(header.getKey()).matches()) {
                throw new IllegalArgumentException("Invalid header name");
            }
            requireHeaderValue(header.getValue());
        }
        headers = Map.copyOf(headers);
    }

    /**
     * Builds the response for a refusal in a format. The status comes from the reason, the
     * challenge is added for {@code 401}, and {@code X-Content-Type-Options: nosniff} stops a
     * browser treating an error body as HTML. A format's own headers can't replace these.
     */
    public static FormatResponse refusal(FormatModule format, Refusal refusal) {
        RefusalBody rendered = format.render(refusal);
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(rendered.headers());
        // Removed before setting, so the canonical name replaces a format's spelling of it too.
        headers.remove("X-Content-Type-Options");
        headers.remove("WWW-Authenticate");
        headers.put("X-Content-Type-Options", "nosniff");
        if (refusal.challenge() != null) {
            headers.put("WWW-Authenticate", refusal.challenge());
        }
        return new FormatResponse(refusal.reason().status(), rendered.contentType(), rendered.body(), headers);
    }

    /** Refuses CR, LF and other control characters, which would split or inject headers. */
    static void requireHeaderValue(String value) {
        if (value.chars().anyMatch(c -> (c < 0x20 && c != '\t') || c == 0x7f)) {
            throw new IllegalArgumentException("Header values must not contain control characters");
        }
    }
}
