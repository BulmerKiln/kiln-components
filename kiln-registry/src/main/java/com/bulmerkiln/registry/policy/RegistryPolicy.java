/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.policy;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.registry.service.BlockService;
import java.util.Optional;

/**
 * Answers the formats' {@link ArtifactPolicy} port from blocks and tombstones (REG-4). Formats that
 * write call {@link BlockService} inside their own transaction as well, so the check and the write
 * commit together.
 */
public final class RegistryPolicy implements ArtifactPolicy {

    private final Database database;
    private final BlockService blocks;

    public RegistryPolicy(Database database, BlockService blocks) {
        this.database = database;
        this.blocks = blocks;
    }

    @Override
    public Optional<Refusal> checkRead(ArtifactRef artifact) {
        return database.transaction(() -> blocks.checkRead(artifact));
    }

    @Override
    public Optional<Refusal> checkWrite(ArtifactRef artifact, long bytes) {
        return database.transaction(() -> blocks.checkWrite(artifact));
    }
}
