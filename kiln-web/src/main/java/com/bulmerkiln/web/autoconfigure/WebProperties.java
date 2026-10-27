/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.autoconfigure;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The web layer's settings, under {@code kiln.web}.
 *
 * @param externalUrl the URL clients reach Kiln at, such as {@code https://pkg.example.com}. Absolute
 *     URLs in responses ({@code Location}, token realms, links) are built from it, never from the
 *     request's {@code Host} header, which the client controls (WEB-5)
 */
@ConfigurationProperties("kiln.web")
public record WebProperties(@Nullable URI externalUrl) {

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
