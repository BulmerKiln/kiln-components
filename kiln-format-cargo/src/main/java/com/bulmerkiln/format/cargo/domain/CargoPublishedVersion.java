/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.domain;

import com.bulmerkiln.registry.domain.PackageVersion;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A published crate version: its index line as stored, and whether it's yanked. */
@Value
public final class CargoPublishedVersion {

    private final PackageVersion stored;

    private final String indexEntry;

    private final boolean yanked;

    public UUID getId() {
        return stored.getId();
    }

    public String getVersion() {
        return stored.getVersion();
    }

    public Instant getPublishedAt() {
        return stored.getPublishedAt();
    }
}
