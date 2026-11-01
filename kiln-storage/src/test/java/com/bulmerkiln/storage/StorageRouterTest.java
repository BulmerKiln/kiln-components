/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageRouterTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");

    /** STO-1: the current store is the one routed for the organization in context, and none outside one. */
    @Test
    void routesTheOrganizationInContext() {
        InMemoryBlobStore acme = new InMemoryBlobStore();
        StorageRouter router = organization -> organization.equals(ACME) ? acme : new InMemoryBlobStore();

        assertThat(OrganizationContext.call(ACME, router::current)).isSameAs(acme);
        assertThatIllegalStateException().isThrownBy(router::current);
    }
}
