/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Who an IdP says signed in. Only {@code issuer} and {@code subject} identify the person; the
 * email is a hint for display and discovery, never an identity (AUTH-7).
 *
 * @param email present only when the IdP vouches that it's verified
 * @param groups IdP group names, which kiln-permissions maps to teams; empty when the
 *     IdP sent none, which means no roles until an admin grants some
 */
public record ExternalIdentity(
        ConnectorId connector,
        String issuer,
        String subject,
        @Nullable String email,
        Set<String> groups) {

    /** Longer subjects aren't produced by any supported IdP; refusing them bounds storage. */
    public static final int MAX_SUBJECT_LENGTH = 255;

    public static final int MAX_GROUPS = 1000;
    public static final int MAX_GROUP_LENGTH = 255;

    public ExternalIdentity {
        Objects.requireNonNull(connector, "connector");
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("The issuer must not be blank");
        }
        if (subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH || IdentityRules.hasControl(subject)) {
            throw new IllegalArgumentException("The subject must be 1 to " + MAX_SUBJECT_LENGTH + " characters");
        }
        if (groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("More than " + MAX_GROUPS + " groups");
        }
        for (String group : groups) {
            if (group.isBlank() || group.length() > MAX_GROUP_LENGTH || IdentityRules.hasControl(group)) {
                throw new IllegalArgumentException("Group names must be 1 to " + MAX_GROUP_LENGTH + " characters");
            }
        }
        groups = Set.copyOf(groups);
    }
}
