/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.net.URI;

/** Rules for the IdP URLs connectors are configured with. */
public final class Endpoints {

    private Endpoints() {}

    /**
     * Checks that {@code uri} is an absolute HTTPS URL with no user info or fragment. Plain HTTP is
     * allowed only when {@code allowInsecure} is set, for a local IdP such as the Compose
     * quick-start's Dex, and never with a query string, which can't appear in an issuer.
     *
     * @param what names the setting in the error message
     */
    public static URI requireSecure(URI uri, boolean allowInsecure, String what) {
        String scheme = uri.getScheme();
        boolean https = "https".equals(scheme);
        if (!https && !(allowInsecure && "http".equals(scheme))) {
            throw new IllegalArgumentException(what + " must be an https URL");
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    what + " must be a plain URL with a host, without user info or a fragment");
        }
        return uri;
    }
}
