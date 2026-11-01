/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.autoconfigure;

import java.net.URI;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The web layer's settings, under {@code kiln.web}.
 */
@ConfigurationProperties("kiln.web")
@Value
public final class WebProperties {

    /**
     * The URL clients reach Kiln at, such as {@code https://pkg.example.com}. Absolute URLs in
     * responses ({@code Location}, token realms, links) are built from it, never from the request's
     * {@code Host} header, which the client controls (WEB-5).
     */
    private final @Nullable URI externalUrl;

    /**
     * Cross-origin settings; Kiln always allows its own headers and methods, and the operator adds
     * origins and any extra headers or methods a web UI needs.
     */
    private final @Nullable Cors cors;

    /**
     * CORS settings, under {@code kiln.web.cors}. Kiln combines these with its own mandatory
     * headers ({@code Authorization}, {@code Kiln-Organization}, {@code Kiln-Format},
     * {@code Content-Type}) and methods ({@code GET}, {@code POST}, {@code PUT}, {@code PATCH},
     * {@code DELETE}, {@code OPTIONS}); the operator's lists are additive.
     */
    @Value
    public static final class Cors {

        /** Origins allowed to make cross-origin requests; empty or absent means no CORS (same-origin only). */
        private final @Nullable List<String> allowedOrigins;

        /** Extra headers the operator's UI needs beyond Kiln's own. */
        private final @Nullable List<String> allowedHeaders;

        /** Extra methods beyond Kiln's own. */
        private final @Nullable List<String> allowedMethods;
    }

    /** The external URL without a trailing slash, after checking it's a plain http(s) URL. */
    public String externalUrlOrFail() {
        URI url = externalUrl;
        if (url == null) {
            throw new IllegalArgumentException(
                    "kiln.web.external-url must be set to the URL clients reach Kiln at, such as https://pkg.example.com");
        }
        String scheme = url.getScheme();
        boolean plain = url.getRawUserInfo() == null
                && url.getRawQuery() == null
                && url.getRawFragment() == null
                && url.getHost() != null;
        if (!("https".equals(scheme) || "http".equals(scheme)) || !plain) {
            throw new IllegalArgumentException(
                    "kiln.web.external-url must be an http(s) URL with a host and no user info, query or fragment");
        }
        String text = url.toString();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }
}
