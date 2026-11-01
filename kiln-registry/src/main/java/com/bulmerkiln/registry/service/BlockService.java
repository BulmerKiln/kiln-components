/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.service;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.Publisher;
import com.bulmerkiln.registry.entity.BlockEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import com.bulmerkiln.registry.repository.BlockRepository;
import com.bulmerkiln.registry.repository.TombstoneRepository;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Blocks and tombstones, checked inside the same transaction as the write they guard, so two
 * concurrent pushes can't race past them (REG-4). A block is {@code 403}, never {@code 404}.
 *
 * <p>Blocks are set by hand on one version or a whole package. The tables also hold version ranges,
 * digests, feed and scanner sources, lifted blocks and tag tombstones; the controls that set them
 * aren't built yet.
 */
public final class BlockService {

    private static final String VERSION = "version";

    private final BlockRepository blocks;
    private final TombstoneRepository tombstones;

    public BlockService(BlockRepository blocks, TombstoneRepository tombstones) {
        this.blocks = blocks;
        this.tombstones = tombstones;
    }

    /**
     * Blocks one version of an artifact, or every version when {@code version} is null. A target
     * that's already blocked keeps the block it has.
     */
    public void block(ArtifactFormat format, String name, @Nullable String version, String reason, Principal by) {
        Publisher setter = Publisher.of(by)
                .orElseThrow(() -> new IllegalArgumentException("A block is set by a member or service account"));
        blocks.addIfAbsent(new BlockEntity(
                new RegistryKey(organization(), UUID.randomUUID()),
                format.getId(),
                name,
                version == null ? "package" : VERSION,
                version == null ? "" : version,
                reason,
                "manual",
                setter.getKind(),
                setter.getId()));
    }

    /** Reserves a deleted version, so it can never be published again. */
    public void tombstone(ArtifactFormat format, String name, String version) {
        tombstones.addIfAbsent(organization(), format.getId(), name, VERSION, version);
    }

    /** Why reading {@code artifact} is refused, if it is: a block on it or on every version. */
    public Optional<Refusal> checkRead(ArtifactRef artifact) {
        String version = artifact.getVersion();
        return blocks.findReason(
                        organization(),
                        artifact.getFormat().getId(),
                        artifact.getName(),
                        version == null ? "" : version)
                .map(reason -> Refusal.of(
                        Reason.BLOCKED,
                        artifact.getName() + (version == null ? "" : " " + version) + " is blocked: " + reason));
    }

    /** Why writing {@code artifact} is refused, if it is: a block, or a tombstone on its version. */
    public Optional<Refusal> checkWrite(ArtifactRef artifact) {
        Optional<Refusal> blocked = checkRead(artifact);
        String version = artifact.getVersion();
        if (blocked.isPresent() || version == null) {
            return blocked;
        }
        boolean tombstoned =
                tombstones.exists(organization(), artifact.getFormat().getId(), artifact.getName(), VERSION, version);
        return tombstoned
                ? Optional.of(Refusal.of(
                        Reason.CONFLICT,
                        artifact.getName() + " " + version + " was deleted and can't be published again"))
                : Optional.empty();
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
