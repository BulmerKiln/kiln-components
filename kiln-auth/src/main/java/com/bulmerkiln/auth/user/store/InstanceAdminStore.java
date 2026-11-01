/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.store;

import com.bulmerkiln.auth.user.constant.InstanceAdminSource;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/** Instance admin grants: an instance table, inside the caller's transaction. */
public final class InstanceAdminStore {

    private InstanceAdminStore() {}

    public static boolean isAdmin(DSLContext tx, UUID userId) {
        return tx.fetchExists(tx.selectOne().from("instance_admins").where("user_id = ?", userId));
    }

    /** Grants instance admin, unless the user already holds it from either source. */
    public static void grant(DSLContext tx, UUID userId, InstanceAdminSource source, @Nullable UUID grantedBy) {
        tx.execute(
                "INSERT INTO instance_admins (user_id, source, granted_by) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                userId,
                source.stored(),
                grantedBy);
    }

    /** The users whose grant came from the bootstrap config. */
    public static List<UUID> bootstrapped(DSLContext tx) {
        return tx.fetch("SELECT user_id FROM instance_admins WHERE source = 'bootstrap'")
                .map(row -> row.get("user_id", UUID.class));
    }

    public static void revokeBootstrapped(DSLContext tx, UUID userId) {
        tx.execute("DELETE FROM instance_admins WHERE user_id = ? AND source = 'bootstrap'", userId);
    }
}
