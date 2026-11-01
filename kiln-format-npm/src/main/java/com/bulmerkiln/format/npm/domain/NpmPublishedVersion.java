/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.registry.domain.PackageVersion;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A published version and its document: the version's {@code package.json} as published. */
@Value
public final class NpmPublishedVersion {

    private final PackageVersion stored;

    private final String document;

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
