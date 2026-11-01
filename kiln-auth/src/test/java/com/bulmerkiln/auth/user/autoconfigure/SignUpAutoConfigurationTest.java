/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.user.autoconfigure.SignUpProperties.AllowedDomain;
import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignUpAutoConfigurationTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    private static final OrganizationDirectory DIRECTORY = new OrganizationDirectory() {
        @Override
        public Optional<Organization> byId(OrganizationId id) {
            return id.equals(ACME.getId()) ? Optional.of(ACME) : Optional.empty();
        }

        @Override
        public Optional<Organization> bySlug(String slug) {
            return slug.equals(ACME.getSlug()) ? Optional.of(ACME) : Optional.empty();
        }

        @Override
        public List<Organization> all() {
            return List.of(ACME);
        }
    };

    /** AUTH-23: an allowed domain is stored lowercase, for its connector and organization. */
    @Test
    void readsAllowedDomains() {
        assertThat(SignUpAutoConfiguration.allowedDomains(signUp("google", "ACME.com", "acme"), DIRECTORY, null))
                .containsExactly(new AllowedDomainEntity(
                        "google", "acme.com", ACME.getId().getValue(), AllowedDomainSource.CONFIG));
    }

    /** AUTH-23: an allowed domain with a missing or invalid part stops the boot. */
    @Test
    void refusesInvalidAllowedDomains() {
        assertThatThrownBy(
                        () -> SignUpAutoConfiguration.allowedDomains(signUp(null, "acme.com", "acme"), DIRECTORY, null))
                .hasMessageContaining("connector");
        assertThatThrownBy(() ->
                        SignUpAutoConfiguration.allowedDomains(signUp("google", "*.acme.com", "acme"), DIRECTORY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        SignUpAutoConfiguration.allowedDomains(signUp("google", "acme.com", "nobody"), DIRECTORY, null))
                .hasMessageContaining("organization nobody");
        assertThatThrownBy(() ->
                        SignUpAutoConfiguration.allowedDomains(signUp("Google!", "acme.com", "acme"), DIRECTORY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** AUTH-24: an admin subject needs both an issuer and a subject. */
    @Test
    void refusesHalfAnAdminSubject() {
        var properties =
                new AdminBootstrapProperties(null, List.of(new AdminBootstrapProperties.AdminSubject(null, "root")));

        assertThatThrownBy(properties::toBootstrapAdmins).hasMessageContaining("issuer");
    }

    private static SignUpProperties signUp(String connector, String domain, String organization) {
        return new SignUpProperties(List.of(new AllowedDomain(connector, domain, organization)));
    }
}
