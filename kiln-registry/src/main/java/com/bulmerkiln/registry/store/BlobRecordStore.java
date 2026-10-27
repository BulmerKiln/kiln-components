/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.store;

import com.bulmerkiln.core.digest.Digest;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * The organization's record of the blobs it holds, whichever format stored them. Recording the same
 * blob twice, as two concurrent pushes do, is harmless (REG-2).
 */
public final class BlobRecordStore {

    private BlobRecordStore() {}

    /** Records a blob the store now holds, or marks an existing one as referenced again. */
    public static void record(DSLContext tx, Digest digest, long size) {
        tx.execute(
                "INSERT INTO blobs (organization_id, digest, size) VALUES (?, ?, ?)"
                        + " ON CONFLICT (organization_id, digest) DO UPDATE SET last_referenced_at = now()",
                NamespaceStore.organization(),
                digest.toString(),
                size);
    }

    /** The size of a blob the organization holds, or empty if it holds none with that digest. */
    public static Optional<Long> size(DSLContext tx, Digest digest) {
        return tx.fetchOptional(
                        "SELECT size FROM blobs WHERE organization_id = ? AND digest = ?",
                        NamespaceStore.organization(),
                        digest.toString())
                .map(row -> row.get(0, Long.class));
    }
}
