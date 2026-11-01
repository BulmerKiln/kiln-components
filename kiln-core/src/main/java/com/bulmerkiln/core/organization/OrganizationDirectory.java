/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import java.util.List;
import java.util.Optional;

/** Where organizations come from. The service that assembles Kiln provides it. */
public interface OrganizationDirectory {

    Optional<Organization> byId(OrganizationId id);

    Optional<Organization> bySlug(String slug);

    /** Every active organization, for jobs that run once per organization, one after another. */
    List<Organization> all();
}
