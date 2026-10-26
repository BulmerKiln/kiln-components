/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.ExternalIdentity;
import com.bulmerkiln.auth.connector.IdentityRules;
import com.bulmerkiln.auth.connector.InvalidIdentityException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Turns a user-info response, plus any groups fetched separately, into an identity. */
public final class OAuth2UserMapper {

    private OAuth2UserMapper() {}

    /**
     * Maps a user-info response.
     *
     * @param extraGroups groups from other API calls, such as {@link #gitHubTeams}
     */
    public static ExternalIdentity map(
            OAuth2Connector connector, Map<String, ?> userInfo, Collection<String> extraGroups) {
        OAuth2Connector.UserPaths paths = connector.paths();
        String subject = IdentityRules.subject(subject(paths.userId().read(userInfo)));
        Set<String> groups = new LinkedHashSet<>();
        JsonPath groupsPath = paths.groups();
        if (groupsPath != null) {
            groups.addAll(IdentityRules.groups(groupsPath.read(userInfo)));
        }
        groups.addAll(IdentityRules.groups(List.copyOf(extraGroups)));
        try {
            return new ExternalIdentity(connector.id(), connector.issuer(), subject, email(paths, userInfo), groups);
        } catch (IllegalArgumentException e) {
            throw new InvalidIdentityException("The user-info response has unusable groups");
        }
    }

    /** Trusted only when the connector also says where the IdP marks it verified (AUTH-OAUTH2-2). */
    private static @Nullable String email(OAuth2Connector.UserPaths paths, Map<String, ?> userInfo) {
        JsonPath email = paths.email();
        JsonPath verified = paths.emailVerified();
        if (email == null || verified == null) {
            return null;
        }
        return IdentityRules.verifiedEmail(email.read(userInfo), verified.read(userInfo));
    }

    /**
     * Groups from GitHub's {@code GET /user/teams}: each team gives {@code org} and
     * {@code org/team-slug}, lowercased, since GitHub logins and slugs are case-insensitive.
     */
    public static Set<String> gitHubTeams(List<?> teams) {
        Set<String> groups = new LinkedHashSet<>();
        JsonPath org = JsonPath.parse("organization.login");
        for (Object item : teams) {
            if (item instanceof Map<?, ?> team
                    && org.read(team) instanceof String login
                    && team.get("slug") instanceof String slug
                    && !login.isBlank()
                    && !slug.isBlank()) {
                String lowerLogin = login.toLowerCase(Locale.ROOT);
                groups.add(lowerLogin);
                groups.add(lowerLogin + "/" + slug.toLowerCase(Locale.ROOT));
            }
        }
        return groups;
    }

    /** A string, or an integer as JSON gives it. Never a float, boolean or object. */
    private static @Nullable String subject(@Nullable Object value) {
        return switch (value) {
            case String s -> s;
            case Integer i -> i.toString();
            case Long l -> l.toString();
            case BigInteger b -> b.toString();
            case null, default -> null;
        };
    }
}
