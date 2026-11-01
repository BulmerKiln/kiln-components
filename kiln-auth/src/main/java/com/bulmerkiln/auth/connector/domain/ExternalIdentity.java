/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.domain;

import com.bulmerkiln.auth.connector.util.IdentityUtils;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Who an IdP says signed in. Only {@code issuer} and {@code subject} identify the person; the
 * email is a hint for display and discovery, never an identity (AUTH-7).
 */
@Value
public final class ExternalIdentity {

    private final ConnectorId connector;

    private final String issuer;

    private final String subject;

    /** Present only when the IdP vouches that it's verified. */
    private final @Nullable String email;

    /**
     * IdP group names, which kiln-permissions maps to teams; empty when the IdP sent none, which means
     * no roles until an admin grants some.
     */
    private final Set<String> groups;

    /** Longer subjects aren't produced by any supported IdP; refusing them bounds storage. */
    public static final int MAX_SUBJECT_LENGTH = 255;

    public static final int MAX_GROUPS = 1000;
    public static final int MAX_GROUP_LENGTH = 255;

    public ExternalIdentity(
            ConnectorId connector, String issuer, String subject, @Nullable String email, Set<String> groups) {
        Objects.requireNonNull(connector, "connector");
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("The issuer must not be blank");
        }
        if (subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH || IdentityUtils.hasControl(subject)) {
            throw new IllegalArgumentException("The subject must be 1 to " + MAX_SUBJECT_LENGTH + " characters");
        }
        if (groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("More than " + MAX_GROUPS + " groups");
        }
        for (String group : groups) {
            if (group.isBlank() || group.length() > MAX_GROUP_LENGTH || IdentityUtils.hasControl(group)) {
                throw new IllegalArgumentException("Group names must be 1 to " + MAX_GROUP_LENGTH + " characters");
            }
        }
        this.groups = Set.copyOf(groups);
        this.connector = connector;
        this.issuer = issuer;
        this.subject = subject;
        this.email = email;
    }
}
