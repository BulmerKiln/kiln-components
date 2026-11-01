/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.autoconfigure;

import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How people who aren't users yet may get in, under {@code kiln.auth}. Sign-ups are off: without
 * an allowed domain, only an invite link or the bootstrap config creates a user (AUTH-20).
 */
@ConfigurationProperties("kiln.auth")
@Value
public final class SignUpProperties {

    /** Verified emails at each domain, signing in through its connector, create a user who joins its organization. */
    private final @Nullable List<AllowedDomain> allowedDomains;

    /**
     * One allowed domain.
     */
    @Value
    public static final class AllowedDomain {

        /** The connector's ID; only sign-ins through it count. */
        private final @Nullable String connector;

        /** Matched exactly: a subdomain needs its own entry. */
        private final @Nullable String domain;

        /** The slug of the organization new users join. */
        private final @Nullable String organization;
    }

    public List<AllowedDomain> allowedDomainsOrNone() {
        return allowedDomains == null ? List.of() : allowedDomains;
    }
}
