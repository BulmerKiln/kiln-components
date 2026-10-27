/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import com.bulmerkiln.core.artifact.ArtifactFormat;

/**
 * A registry format. A module implementing it, registered as a bean by its auto-configuration, is
 * picked up by the {@link FormatRegistry} and switched with {@code kiln.format.<id>.enabled}
 * without any further wiring. Every implementation must pass {@code FormatModuleContract}.
 */
public interface FormatModule {

    ArtifactFormat format();

    /**
     * The first path segment of the format's routes on the registry host, such as {@code v2} or
     * {@code npm}. It shares the path space with organization slugs, so it must be one of
     * {@link com.bulmerkiln.core.organization.Organization#RESERVED_SLUGS}.
     */
    String pathSegment();

    /** Renders a refusal in this format's error body. Must escape {@link Refusal#message()}. */
    RefusalBody render(Refusal refusal);
}
