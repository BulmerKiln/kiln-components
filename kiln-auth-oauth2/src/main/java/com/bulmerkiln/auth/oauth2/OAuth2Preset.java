/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Endpoints and paths for known OAuth2 IdPs; a generic connector configures them itself. */
public enum OAuth2Preset {
    GENERIC(null, null, null, null, null, List.of()),
    /**
     * GitHub (or GitHub Enterprise Server, by overriding the URLs). The subject is the numeric
     * {@code id}, which never changes, never the {@code login}, which can be renamed and then
     * registered by someone else. Groups come from {@code /user/teams}, as {@code organization} and
     * {@code organization/team}.
     */
    GITHUB(
            "https://github.com",
            "https://github.com/login/oauth/authorize",
            "https://github.com/login/oauth/access_token",
            "https://api.github.com/user",
            "id",
            List.of("read:user", "user:email", "read:org"));

    private final @Nullable String issuer;
    private final @Nullable URI authorizationUri;
    private final @Nullable URI tokenUri;
    private final @Nullable URI userInfoUri;
    private final @Nullable String userIdPath;

    @SuppressWarnings("ImmutableEnumChecker") // Always a List.of, which is immutable.
    private final List<String> scopes;

    OAuth2Preset(
            @Nullable String issuer,
            @Nullable String authorizationUri,
            @Nullable String tokenUri,
            @Nullable String userInfoUri,
            @Nullable String userIdPath,
            List<String> scopes) {
        this.issuer = issuer;
        this.authorizationUri = authorizationUri == null ? null : URI.create(authorizationUri);
        this.tokenUri = tokenUri == null ? null : URI.create(tokenUri);
        this.userInfoUri = userInfoUri == null ? null : URI.create(userInfoUri);
        this.userIdPath = userIdPath;
        this.scopes = scopes;
    }

    public @Nullable String issuer() {
        return issuer;
    }

    public @Nullable URI authorizationUri() {
        return authorizationUri;
    }

    public @Nullable URI tokenUri() {
        return tokenUri;
    }

    public @Nullable URI userInfoUri() {
        return userInfoUri;
    }

    public @Nullable String userIdPath() {
        return userIdPath;
    }

    public List<String> scopes() {
        return scopes;
    }
}
