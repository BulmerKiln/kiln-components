/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.registry.store.BlockStore;
import java.util.Optional;

/**
 * Answers the formats' {@link ArtifactPolicy} port from blocks and tombstones (REG-4). Formats that
 * write call {@link BlockStore} inside their own transaction as well, so the check and the write commit
 * together.
 */
public final class RegistryPolicy implements ArtifactPolicy {

    private final Database database;

    public RegistryPolicy(Database database) {
        this.database = database;
    }

    @Override
    public Optional<Refusal> checkRead(ArtifactRef artifact) {
        return database.transaction(tx -> BlockStore.checkRead(tx, artifact));
    }

    @Override
    public Optional<Refusal> checkWrite(ArtifactRef artifact, long bytes) {
        return database.transaction(tx -> BlockStore.checkWrite(tx, artifact));
    }
}
