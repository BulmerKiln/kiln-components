/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Organizations to create on start, under {@code kiln.bootstrap}: boot config, for installations
 * to begin with (DB-10).
 *
 * @param organizations the slugs of organizations to create if they're missing; none is ever
 *     deleted for leaving the list
 */
@ConfigurationProperties("kiln.bootstrap")
public record OrganizationBootstrapProperties(@Nullable List<String> organizations) {

    public List<String> organizationsOrNone() {
        return organizations == null ? List.of() : organizations;
    }
}
