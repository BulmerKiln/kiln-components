/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Organizations to create on start, under {@code kiln.bootstrap}: boot config, for installations
 * to begin with (DB-10).
 */
@ConfigurationProperties("kiln.bootstrap")
@Value
public final class OrganizationBootstrapProperties {

    /** The slugs of organizations to create if they're missing; none is ever deleted for leaving the list. */
    private final @Nullable List<String> organizations;

    public List<String> organizationsOrNone() {
        return organizations == null ? List.of() : organizations;
    }
}
