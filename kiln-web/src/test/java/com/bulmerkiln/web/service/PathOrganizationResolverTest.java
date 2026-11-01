/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.core.organization.OrganizationResolver.Resolution;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** WEB-6: the organization is the first path segment. */
class PathOrganizationResolverTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");

    private static final OrganizationDirectory ONE = new OrganizationDirectory() {
        @Override
        public Optional<Organization> byId(OrganizationId id) {
            return ACME.getId().equals(id) ? Optional.of(ACME) : Optional.empty();
        }

        @Override
        public Optional<Organization> bySlug(String slug) {
            return ACME.getSlug().equals(slug) ? Optional.of(ACME) : Optional.empty();
        }

        @Override
        public List<Organization> all() {
            return List.of(ACME);
        }
    };

    private final PathOrganizationResolver resolver = new PathOrganizationResolver(ONE);

    @Test
    void takesTheOrganizationFromTheFirstSegment() {
        assertThat(resolver.resolve("acme/team/app/manifests/1"))
                .contains(new Resolution(ACME, "team/app/manifests/1"));
        assertThat(resolver.resolve("acme/@acme%2futil")).contains(new Resolution(ACME, "@acme%2futil"));
        assertThat(resolver.resolve("acme")).contains(new Resolution(ACME, ""));
        assertThat(resolver.resolve("acme/")).contains(new Resolution(ACME, ""));
    }

    @Test
    void resolvesNothingElse() {
        assertThat(resolver.resolve("globex/app")).isEmpty();
        assertThat(resolver.resolve("ACME/app")).isEmpty();
        assertThat(resolver.resolve("/acme/app")).isEmpty();
        assertThat(resolver.resolve("")).isEmpty();
    }

    /** Whatever path a request carries, resolution never throws. */
    @FuzzTest
    void resolutionNeverThrows(FuzzedDataProvider data) {
        resolver.resolve(data.consumeString(512));
    }
}
