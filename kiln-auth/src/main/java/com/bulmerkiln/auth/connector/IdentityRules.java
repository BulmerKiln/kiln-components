/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The rules every connector applies to what its IdP sent, so a person is identified the same way
 * whichever IdP they used. Errors never contain the values they reject.
 */
public final class IdentityRules {

    /** RFC 5321's limit for a mailbox. */
    public static final int MAX_EMAIL_LENGTH = 320;

    private IdentityRules() {}

    /** The subject, which must be 1 to 255 printable characters. */
    public static String subject(@Nullable String subject) {
        if (subject == null
                || subject.isBlank()
                || subject.length() > ExternalIdentity.MAX_SUBJECT_LENGTH
                || hasControl(subject)) {
            throw new InvalidIdentityException("The IdP response has no usable subject");
        }
        return subject;
    }

    /**
     * The email, only when the IdP marks it verified with a JSON {@code true}. Otherwise none: an
     * attacker who can set an unverified address at the IdP must not be matched to its owner.
     */
    public static @Nullable String verifiedEmail(@Nullable Object email, @Nullable Object verified) {
        if (!Boolean.TRUE.equals(verified) || !(email instanceof String address)) {
            return null;
        }
        if (address.length() > MAX_EMAIL_LENGTH || address.indexOf('@') <= 0 || hasControl(address)) {
            return null;
        }
        return address;
    }

    /**
     * Groups from a claim that holds one name or a list of them. Non-strings, blank names and names
     * with control characters are skipped, since dropping a group only ever removes access.
     *
     * @throws InvalidIdentityException for more groups, or longer names, than an identity may hold
     */
    public static Set<String> groups(@Nullable Object claim) {
        Set<String> groups = new LinkedHashSet<>();
        if (claim instanceof String single) {
            add(groups, single);
        } else if (claim instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String group) {
                    add(groups, group);
                }
            }
        }
        if (groups.size() > ExternalIdentity.MAX_GROUPS) {
            throw new InvalidIdentityException(
                    "The IdP response lists more than " + ExternalIdentity.MAX_GROUPS + " groups");
        }
        return groups;
    }

    private static void add(Set<String> groups, String group) {
        if (group.isBlank() || hasControl(group)) {
            return;
        }
        if (group.length() > ExternalIdentity.MAX_GROUP_LENGTH) {
            throw new InvalidIdentityException(
                    "The IdP response has a group name over " + ExternalIdentity.MAX_GROUP_LENGTH + " characters");
        }
        groups.add(group);
    }

    static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
