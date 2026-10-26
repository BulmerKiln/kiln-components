/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.Optional;

/**
 * The port formats ask before serving or accepting an artifact: blocks, tombstones, immutability
 * and quotas. The policy and quota subsystems implement it; there's deliberately no allow-all
 * default, so an assembly without a policy fails to start rather than serving unchecked.
 */
public interface ArtifactPolicy {

    /** A refusal if the artifact may not be served, such as a block. */
    Optional<Refusal> checkRead(ArtifactRef artifact);

    /**
     * A refusal if the artifact may not be written, such as an immutable tag, a tombstone or the
     * quota. It must be called in the same transaction as the write, or two pushes can race past it.
     *
     * @param bytes the new bytes the write would add
     */
    Optional<Refusal> checkWrite(ArtifactRef artifact, long bytes);
}
