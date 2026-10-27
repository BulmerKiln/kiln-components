/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.store;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.oci.domain.OciRepositoryName;
import com.bulmerkiln.registry.domain.Namespace;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Repositories, the blobs linked to each, and their upload sessions, inside the caller's transaction. */
public final class OciRepositoryStore {

    private OciRepositoryStore() {}

    public static Optional<UUID> find(DSLContext tx, OciRepositoryName name) {
        return tx.fetchOptional(
                        "SELECT id FROM repositories WHERE organization_id = ? AND name = ?",
                        organization(),
                        name.value())
                .map(row -> row.get(0, UUID.class));
    }

    /** The repository, created on first push in the namespace that owns its name. */
    public static UUID ensure(DSLContext tx, OciRepositoryName name, Namespace namespace) {
        tx.execute(
                "INSERT INTO repositories (organization_id, id, namespace_id, name) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (organization_id, name) DO NOTHING",
                organization(),
                UUID.randomUUID(),
                namespace.id(),
                name.value());
        return find(tx, name).orElseThrow();
    }

    /** Lets the repository read a blob the organization holds. Linking twice is harmless. */
    public static void link(DSLContext tx, UUID repository, Digest digest) {
        tx.execute(
                "INSERT INTO repository_blobs (organization_id, repository_id, digest) VALUES (?, ?, ?)"
                        + " ON CONFLICT DO NOTHING",
                organization(),
                repository,
                digest.toString());
    }

    public static boolean linked(DSLContext tx, UUID repository, Digest digest) {
        return tx.fetchExists(tx.selectOne()
                .from("repository_blobs")
                .where(
                        "organization_id = ? AND repository_id = ? AND digest = ?",
                        organization(),
                        repository,
                        digest.toString()));
    }

    public static void startUpload(DSLContext tx, UUID repository, UUID session) {
        tx.execute(
                "INSERT INTO upload_sessions (organization_id, id, repository_id) VALUES (?, ?, ?)",
                organization(),
                session,
                repository);
    }

    /** Whether {@code session} was started through {@code repository}. */
    public static boolean uploadBelongs(DSLContext tx, UUID repository, UUID session) {
        return tx.fetchExists(tx.selectOne()
                .from("upload_sessions")
                .where("organization_id = ? AND id = ? AND repository_id = ?", organization(), session, repository));
    }

    public static void endUpload(DSLContext tx, UUID session) {
        tx.execute("DELETE FROM upload_sessions WHERE organization_id = ? AND id = ?", organization(), session);
    }

    static UUID organization() {
        return OrganizationContext.current().id().value();
    }
}
