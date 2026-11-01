/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.entity;

import java.util.UUID;
import lombok.Value;

/** A plugin's key: the registry package of the plugin artifact. */
@Value
public final class MavenPluginKey {

    private final UUID organizationId;

    private final UUID packageId;
}
