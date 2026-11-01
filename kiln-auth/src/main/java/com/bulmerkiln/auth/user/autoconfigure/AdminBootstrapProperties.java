/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.autoconfigure;

import com.bulmerkiln.auth.user.domain.BootstrapAdmins;
import com.bulmerkiln.auth.user.domain.IssuerSubject;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first instance admins, under {@code kiln.bootstrap}: boot config, reconciled with the
 * database on every start, so removing an entry revokes its grant (AUTH-24).
 */
@ConfigurationProperties("kiln.bootstrap")
@Value
public final class AdminBootstrapProperties {

    /**
     * Verified emails; the first sign-in with one, through a connector whose allowed domains include
     * its domain, creates the user and makes them an instance admin.
     */
    private final @Nullable List<String> adminEmails;

    /** Issuer and subject pairs, for IdPs that don't send verified emails. */
    private final @Nullable List<AdminSubject> adminSubjects;

    /** One identity, as its IdP names it. */
    @Value
    public static final class AdminSubject {

        private final @Nullable String issuer;

        private final @Nullable String subject;

        IssuerSubject toIssuerSubject() {
            String configuredIssuer = issuer;
            String configuredSubject = subject;
            return new IssuerSubject(
                    configuredIssuer == null ? "" : configuredIssuer,
                    configuredSubject == null ? "" : configuredSubject);
        }
    }

    public BootstrapAdmins toBootstrapAdmins() {
        List<IssuerSubject> subjects = adminSubjects == null
                ? List.of()
                : adminSubjects.stream().map(AdminSubject::toIssuerSubject).toList();
        return BootstrapAdmins.of(adminEmails == null ? List.of() : adminEmails, subjects);
    }
}
