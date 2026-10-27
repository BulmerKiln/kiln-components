/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.store;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.domain.OciDescriptor;
import com.bulmerkiln.format.oci.domain.OciManifest;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/** Manifests, what they reference, and tags, inside the caller's transaction. */
public final class OciManifestStore {

    private OciManifestStore() {}

    /** Stores a manifest and what it references. Storing the same manifest again is harmless. */
    public static void store(DSLContext tx, UUID repository, OciManifest manifest, byte[] content) {
        OciDescriptor subject = manifest.subject();
        int stored = tx.execute(
                "INSERT INTO manifests (organization_id, repository_id, digest, media_type, artifact_type, subject,"
                        + " content) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                OciRepositoryStore.organization(),
                repository,
                manifest.digest().toString(),
                manifest.mediaType(),
                artifactType(manifest),
                subject == null ? null : subject.digest().toString(),
                content);
        if (stored == 0) {
            return;
        }
        for (OciDescriptor blob : manifest.blobs()) {
            reference(tx, repository, manifest.digest(), blob.digest(), "blob");
        }
        for (OciDescriptor child : manifest.manifests()) {
            reference(tx, repository, manifest.digest(), child.digest(), "manifest");
        }
    }

    public static Optional<OciManifestEntity> find(DSLContext tx, UUID repository, Digest digest) {
        return tx.fetchOptional(
                        "SELECT digest, media_type, artifact_type, content FROM manifests"
                                + " WHERE organization_id = ? AND repository_id = ? AND digest = ?",
                        OciRepositoryStore.organization(),
                        repository,
                        digest.toString())
                .map(OciManifestStore::stored);
    }

    public static Optional<Digest> tag(DSLContext tx, UUID repository, String tag) {
        return tx.fetchOptional(
                        "SELECT digest FROM tags WHERE organization_id = ? AND repository_id = ? AND name = ?",
                        OciRepositoryStore.organization(),
                        repository,
                        tag)
                .map(row -> Digest.parse(row.get(0, String.class)));
    }

    /** Points a tag at a manifest, moving it if it pointed elsewhere. */
    public static void setTag(DSLContext tx, UUID repository, String tag, Digest digest) {
        tx.execute(
                "INSERT INTO tags (organization_id, repository_id, name, digest) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (organization_id, repository_id, name)"
                        + " DO UPDATE SET digest = excluded.digest, updated_at = now()",
                OciRepositoryStore.organization(),
                repository,
                tag,
                digest.toString());
    }

    public static boolean deleteTag(DSLContext tx, UUID repository, String tag) {
        return tx.execute(
                        "DELETE FROM tags WHERE organization_id = ? AND repository_id = ? AND name = ?",
                        OciRepositoryStore.organization(),
                        repository,
                        tag)
                > 0;
    }

    /** Whether another manifest in the repository, an index, references this one. */
    public static boolean referencedByIndex(DSLContext tx, UUID repository, Digest digest) {
        return tx.fetchExists(tx.selectOne()
                .from("manifest_refs")
                .where(
                        "organization_id = ? AND repository_id = ? AND child = ? AND kind = 'manifest'",
                        OciRepositoryStore.organization(),
                        repository,
                        digest.toString()));
    }

    /** Deletes a manifest, with the tags pointing at it and what it references. */
    public static boolean delete(DSLContext tx, UUID repository, Digest digest) {
        return tx.execute(
                        "DELETE FROM manifests WHERE organization_id = ? AND repository_id = ? AND digest = ?",
                        OciRepositoryStore.organization(),
                        repository,
                        digest.toString())
                > 0;
    }

    /** Up to {@code limit} tags after {@code last}, in lexical order. */
    public static List<String> tags(DSLContext tx, UUID repository, @Nullable String last, int limit) {
        return tx.fetch(
                        "SELECT name FROM tags WHERE organization_id = ? AND repository_id = ? AND name > ?"
                                + " ORDER BY name COLLATE \"C\" LIMIT ?",
                        OciRepositoryStore.organization(),
                        repository,
                        last == null ? "" : last,
                        limit)
                .map(row -> row.get(0, String.class));
    }

    /** The manifests whose subject is {@code digest}, optionally only those of one artifact type. */
    public static List<OciManifestEntity> referrers(
            DSLContext tx, UUID repository, Digest digest, @Nullable String artifactType) {
        return tx.fetch(
                        "SELECT digest, media_type, artifact_type, content FROM manifests"
                                + " WHERE organization_id = ? AND repository_id = ? AND subject = ?"
                                + " AND (?::text IS NULL OR artifact_type = ?) ORDER BY digest",
                        OciRepositoryStore.organization(),
                        repository,
                        digest.toString(),
                        artifactType,
                        artifactType)
                .map(OciManifestStore::stored);
    }

    /** The manifest's artifact type, or for an image without one, its config's media type. */
    static @Nullable String artifactType(OciManifest manifest) {
        if (manifest.artifactType() != null || manifest.isIndex()) {
            return manifest.artifactType();
        }
        return manifest.blobs().getFirst().mediaType();
    }

    private static void reference(DSLContext tx, UUID repository, Digest digest, Digest child, String kind) {
        tx.execute(
                "INSERT INTO manifest_refs (organization_id, repository_id, digest, child, kind)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                OciRepositoryStore.organization(),
                repository,
                digest.toString(),
                child.toString(),
                kind);
    }

    private static OciManifestEntity stored(Record row) {
        return new OciManifestEntity(
                Digest.parse(row.get("digest", String.class)),
                row.get("media_type", String.class),
                row.get("artifact_type", String.class),
                row.get("content", byte[].class));
    }
}
