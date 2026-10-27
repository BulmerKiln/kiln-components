/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.store;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * PackageStore of the package formats (npm, Maven, Cargo), their versions, and each version's files,
 * inside the caller's transaction. Metadata is the format's own JSON, stored as given. Versions and
 * files are immutable once written (REG-3).
 */
public final class PackageStore {

    private PackageStore() {}

    /** The package whose normalized name is {@code normalizedName}, as each format normalizes. */
    public static Optional<PackageEntity> find(DSLContext tx, ArtifactFormat format, String normalizedName) {
        return tx.fetchOptional(
                        "SELECT id, namespace_id, name, metadata FROM packages"
                                + " WHERE organization_id = ? AND format = ? AND normalized_name = ?",
                        NamespaceStore.organization(),
                        format.id(),
                        normalizedName)
                .map(PackageStore::pkg);
    }

    /**
     * Creates the package, or returns the one already there: two first publishes racing each other
     * both see the same package.
     */
    public static PackageEntity create(
            DSLContext tx, ArtifactFormat format, UUID namespaceId, String name, String normalizedName) {
        tx.execute(
                "INSERT INTO packages (organization_id, id, namespace_id, format, name, normalized_name)"
                        + " VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (organization_id, format, normalized_name) DO NOTHING",
                NamespaceStore.organization(),
                UUID.randomUUID(),
                namespaceId,
                format.id(),
                name,
                normalizedName);
        return find(tx, format, normalizedName).orElseThrow();
    }

    public static void updateMetadata(DSLContext tx, UUID packageId, String metadata) {
        tx.execute(
                "UPDATE packages SET metadata = ? WHERE organization_id = ? AND id = ?",
                metadata,
                NamespaceStore.organization(),
                packageId);
    }

    /** Every version of a package, oldest first. */
    public static List<PackageVersionEntity> versions(DSLContext tx, UUID packageId) {
        return tx.fetch(
                        "SELECT id, version, metadata, yanked, published_at FROM package_versions"
                                + " WHERE organization_id = ? AND package_id = ? ORDER BY published_at, version",
                        NamespaceStore.organization(),
                        packageId)
                .map(PackageStore::version);
    }

    public static Optional<PackageVersionEntity> version(DSLContext tx, UUID packageId, String version) {
        return tx.fetchOptional(
                        "SELECT id, version, metadata, yanked, published_at FROM package_versions"
                                + " WHERE organization_id = ? AND package_id = ? AND version = ?",
                        NamespaceStore.organization(),
                        packageId,
                        version)
                .map(PackageStore::version);
    }

    /** Adds a version; one that already exists is a conflict, since versions never change (REG-3). */
    public static PackageVersionEntity addVersion(DSLContext tx, UUID packageId, String version, String metadata) {
        int added = tx.execute(
                "INSERT INTO package_versions (organization_id, id, package_id, version, metadata)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (organization_id, package_id, version) DO NOTHING",
                NamespaceStore.organization(),
                UUID.randomUUID(),
                packageId,
                version,
                metadata);
        if (added == 0) {
            throw new RefusedException(
                    Refusal.of(Reason.CONFLICT, "Version " + version + " already exists and can't be replaced"));
        }
        return version(tx, packageId, version).orElseThrow();
    }

    /**
     * The version, created if it doesn't exist yet: for formats that publish a version as many
     * separate files, such as Maven, where each file is immutable but the version grows (REG-3).
     */
    public static PackageVersionEntity ensureVersion(DSLContext tx, UUID packageId, String version) {
        tx.execute(
                "INSERT INTO package_versions (organization_id, id, package_id, version) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (organization_id, package_id, version) DO NOTHING",
                NamespaceStore.organization(),
                UUID.randomUUID(),
                packageId,
                version);
        return version(tx, packageId, version).orElseThrow();
    }

    /** Changes a version's metadata where the format allows it, such as npm's deprecation message. */
    public static void updateVersionMetadata(DSLContext tx, UUID versionId, String metadata) {
        tx.execute(
                "UPDATE package_versions SET metadata = ? WHERE organization_id = ? AND id = ?",
                metadata,
                NamespaceStore.organization(),
                versionId);
    }

    public static void setYanked(DSLContext tx, UUID versionId, boolean yanked) {
        tx.execute(
                "UPDATE package_versions SET yanked = ? WHERE organization_id = ? AND id = ?",
                yanked,
                NamespaceStore.organization(),
                versionId);
    }

    /**
     * Adds a file to a version. The same bytes again is a no-op, since clients retry; different
     * bytes under a name that exists is a conflict (REG-3). The blob must already be recorded.
     */
    public static void addFile(DSLContext tx, UUID versionId, String name, Digest digest, long size) {
        Optional<PackageFileEntity> existing = file(tx, versionId, name);
        if (existing.isPresent()) {
            if (!existing.get().digest().equals(digest)) {
                throw new RefusedException(
                        Refusal.of(Reason.CONFLICT, "File " + name + " already exists and can't be replaced"));
            }
            return;
        }
        tx.execute(
                "INSERT INTO package_files (organization_id, version_id, name, digest, size) VALUES (?, ?, ?, ?, ?)",
                NamespaceStore.organization(),
                versionId,
                name,
                digest.toString(),
                size);
    }

    public static Optional<PackageFileEntity> file(DSLContext tx, UUID versionId, String name) {
        return tx.fetchOptional(
                        "SELECT name, digest, size FROM package_files"
                                + " WHERE organization_id = ? AND version_id = ? AND name = ?",
                        NamespaceStore.organization(),
                        versionId,
                        name)
                .map(PackageStore::file);
    }

    public static List<PackageFileEntity> files(DSLContext tx, UUID versionId) {
        return tx.fetch(
                        "SELECT name, digest, size FROM package_files"
                                + " WHERE organization_id = ? AND version_id = ? ORDER BY name",
                        NamespaceStore.organization(),
                        versionId)
                .map(PackageStore::file);
    }

    private static PackageEntity pkg(Record row) {
        return new PackageEntity(
                row.get("id", UUID.class),
                row.get("namespace_id", UUID.class),
                row.get("name", String.class),
                row.get("metadata", String.class));
    }

    private static PackageVersionEntity version(Record row) {
        return new PackageVersionEntity(
                row.get("id", UUID.class),
                row.get("version", String.class),
                row.get("metadata", String.class),
                row.get("yanked", Boolean.class),
                row.get("published_at", OffsetDateTime.class).toInstant());
    }

    private static PackageFileEntity file(Record row) {
        return new PackageFileEntity(
                row.get("name", String.class),
                Digest.parse(row.get("digest", String.class)),
                row.get("size", Long.class));
    }
}
