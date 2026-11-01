/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A published version: immutable, with what a format adds kept in its own tables (REG-3). */
@Value
public final class PackageVersion {

    private final UUID id;

    private final String version;

    private final Instant publishedAt;
}
