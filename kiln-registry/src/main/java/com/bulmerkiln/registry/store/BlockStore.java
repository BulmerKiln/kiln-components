/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.store;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/**
 * BlockStore and tombstones, checked inside the same transaction as the write they guard, so two
 * concurrent pushes can't race past them (REG-4). A block is {@code 403}, never {@code 404}.
 */
public final class BlockStore {

    private BlockStore() {}

    /** BlockStore one version of an artifact, or every version when {@code version} is null. */
    public static void block(
            DSLContext tx, ArtifactFormat format, String name, @Nullable String version, String reason) {
        tx.execute(
                "INSERT INTO blocks (organization_id, format, name, version, reason) VALUES (?, ?, ?, ?, ?)"
                        + " ON CONFLICT (organization_id, format, name, version)"
                        + " DO UPDATE SET reason = excluded.reason",
                NamespaceStore.organization(),
                format.id(),
                name,
                version == null ? "" : version,
                reason);
    }

    /** Reserves a deleted version, so it can never be published again. */
    public static void tombstone(DSLContext tx, ArtifactFormat format, String name, String version) {
        tx.execute(
                "INSERT INTO tombstones (organization_id, format, name, version) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT DO NOTHING",
                NamespaceStore.organization(),
                format.id(),
                name,
                version);
    }

    /** Why reading {@code artifact} is refused, if it is: a block on it or on every version. */
    public static Optional<Refusal> checkRead(DSLContext tx, ArtifactRef artifact) {
        return blockOn(tx, artifact);
    }

    /** Why writing {@code artifact} is refused, if it is: a block, or a tombstone on its version. */
    public static Optional<Refusal> checkWrite(DSLContext tx, ArtifactRef artifact) {
        Optional<Refusal> blocked = blockOn(tx, artifact);
        String version = artifact.version();
        if (blocked.isPresent() || version == null) {
            return blocked;
        }
        boolean tombstoned = tx.fetchExists(tx.selectOne()
                .from("tombstones")
                .where(
                        "organization_id = ? AND format = ? AND name = ? AND version = ?",
                        NamespaceStore.organization(),
                        artifact.format().id(),
                        artifact.name(),
                        version));
        return tombstoned
                ? Optional.of(Refusal.of(
                        Reason.CONFLICT, artifact.name() + " " + version + " was deleted and can't be published again"))
                : Optional.empty();
    }

    private static Optional<Refusal> blockOn(DSLContext tx, ArtifactRef artifact) {
        String version = artifact.version();
        return tx.fetchOptional(
                        "SELECT reason FROM blocks WHERE organization_id = ? AND format = ? AND name = ?"
                                + " AND version IN ('', ?) ORDER BY version LIMIT 1",
                        NamespaceStore.organization(),
                        artifact.format().id(),
                        artifact.name(),
                        version == null ? "" : version)
                .map(row -> Refusal.of(
                        Reason.BLOCKED,
                        artifact.name() + (version == null ? "" : " " + version) + " is blocked: "
                                + row.get(0, String.class)));
    }
}
