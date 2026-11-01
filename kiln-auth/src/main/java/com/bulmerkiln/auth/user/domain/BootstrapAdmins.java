/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.domain;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * The instance admins boot config names: verified emails, claimed through a connector that allows
 * their domain, and issuer and subject pairs for IdPs without verified emails.
 */
@Value
public final class BootstrapAdmins {

    private final Set<String> emails;

    private final Set<IssuerSubject> subjects;

    public static final BootstrapAdmins NONE = new BootstrapAdmins(Set.of(), Set.of());

    public BootstrapAdmins(Set<String> emails, Set<IssuerSubject> subjects) {
        this.emails = emails.stream().map(BootstrapAdmins::normalize).collect(Collectors.toUnmodifiableSet());
        this.subjects = Set.copyOf(subjects);
    }

    public static BootstrapAdmins of(Collection<String> emails, Collection<IssuerSubject> subjects) {
        return new BootstrapAdmins(Set.copyOf(emails), Set.copyOf(subjects));
    }

    public boolean listsEmail(String email) {
        return emails.contains(normalize(email));
    }

    public boolean listsSubject(String issuer, String subject) {
        return subjects.contains(new IssuerSubject(issuer, subject));
    }

    private static String normalize(String email) {
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            throw new IllegalArgumentException("kiln.bootstrap.admin-emails has an entry that isn't an email");
        }
        return email.toLowerCase(Locale.ROOT);
    }
}
