/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Defaults for IdPs whose claims differ from the generic case. A connector can still override the
 * scopes and the groups claim.
 */
public enum OidcPreset {
    GENERIC("groups", List.of("openid", "profile", "email"), false),
    DEX("groups", List.of("openid", "profile", "email", "groups"), false),
    KEYCLOAK("groups", List.of("openid", "profile", "email"), false),
    AUTHENTIK("groups", List.of("openid", "profile", "email"), false),
    ORY("groups", List.of("openid", "profile", "email"), false),
    OKTA("groups", List.of("openid", "profile", "email", "groups"), false),
    GITLAB("groups_direct", List.of("openid", "profile", "email"), false),
    /**
     * Google signs in any Google account, so this preset requires domains and accepts only
     * tokens whose {@code hd} (hosted domain) claim is one of them. Google sends no groups.
     */
    GOOGLE_WORKSPACE(null, List.of("openid", "profile", "email"), true);

    private final @Nullable String groupsClaim;

    @SuppressWarnings("ImmutableEnumChecker") // Always a List.of, which is immutable.
    private final List<String> scopes;

    private final boolean requiresHostedDomain;

    OidcPreset(@Nullable String groupsClaim, List<String> scopes, boolean requiresHostedDomain) {
        this.groupsClaim = groupsClaim;
        this.scopes = scopes;
        this.requiresHostedDomain = requiresHostedDomain;
    }

    public @Nullable String groupsClaim() {
        return groupsClaim;
    }

    public List<String> scopes() {
        return scopes;
    }

    public boolean requiresHostedDomain() {
        return requiresHostedDomain;
    }
}
