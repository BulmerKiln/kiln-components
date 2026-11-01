/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.domain.OciDescriptor;
import com.bulmerkiln.format.oci.domain.OciManifest;
import com.bulmerkiln.format.oci.domain.OciManifestParser;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.dto.OciManifestPushResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import com.bulmerkiln.format.oci.store.OciManifestStore;
import com.bulmerkiln.format.oci.store.OciRepositoryStore;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.store.BlockStore;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/** Pulling, pushing and deleting manifests (FMT-OCI-17 to FMT-OCI-20). */
public final class OciManifestService {

    private final Database database;
    private final OciAccessService access;
    private final ArtifactEventService events;
    private final OciManifestParser parser;

    public OciManifestService(
            Database database, OciAccessService access, ArtifactEventService events, int maxManifestSize) {
        this.database = database;
        this.access = access;
        this.events = events;
        this.parser = new OciManifestParser(maxManifestSize);
    }

    /** A manifest by tag or digest, exactly as pushed. BlockStore apply to the tag and the digest. */
    public OciManifestEntity pull(OciRepositoryRequest request, OciReference reference, boolean head) {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        OciManifestEntity manifest = database.transaction(tx -> resolve(tx, request, reference))
                .orElseThrow(() -> new OciException(OciErrorCode.MANIFEST_UNKNOWN, "Manifest unknown"));
        access.checkRead(request, namespace, reference.toString());
        access.checkRead(request, namespace, manifest.getDigest().toString());
        if (!head) {
            events.pulled(
                    OciAccessService.artifact(request, namespace, reference.toString()), manifest.getContent().length);
        }
        return manifest;
    }

    /**
     * Stores a manifest and, for a tag, points the tag at it. Everything it references must already
     * be in the repository, except a subject, which OCI 1.1 lets be pushed later.
     */
    public OciManifestPushResponse push(
            OciRepositoryRequest request, OciReference reference, @Nullable String contentType, byte[] content) {
        Namespace namespace = access.requireToWrite(request, Operation.PUSH);
        OciManifest manifest = parser.parse(content, contentType);
        if (reference instanceof OciReference.ByDigest byDigest
                && !byDigest.getDigest().equals(manifest.getDigest())) {
            throw new OciException(OciErrorCode.DIGEST_INVALID, "The manifest doesn't match its digest");
        }
        database.run(tx -> {
            refuseIfPolicyBlocks(tx, request, namespace, reference.toString());
            refuseIfPolicyBlocks(tx, request, namespace, manifest.getDigest().toString());
            UUID repository = OciRepositoryStore.ensure(tx, request.getRepository(), namespace);
            requireReferenced(tx, repository, manifest);
            OciManifestStore.store(tx, repository, manifest, content);
            if (reference instanceof OciReference.Tag tag) {
                OciManifestStore.setTag(tx, repository, tag.getValue(), manifest.getDigest());
            }
        });
        events.pushed(OciAccessService.artifact(request, Optional.of(namespace), reference.toString()), content.length);
        OciDescriptor subject = manifest.getSubject();
        return new OciManifestPushResponse(manifest.getDigest(), subject == null ? null : subject.getDigest());
    }

    /**
     * Deletes a tag, or a manifest with the tags pointing at it. A manifest an index still
     * references is refused, so an index never points at nothing (FMT-OCI-20).
     */
    public void delete(OciRepositoryRequest request, OciReference reference) {
        access.require(request, Operation.DELETE);
        boolean deleted = database.transaction(tx -> {
            UUID repository =
                    OciRepositoryStore.find(tx, request.getRepository()).orElse(null);
            if (repository == null) {
                return false;
            }
            return switch (reference) {
                case OciReference.Tag tag -> OciManifestStore.deleteTag(tx, repository, tag.getValue());
                case OciReference.ByDigest byDigest -> {
                    if (OciManifestStore.referencedByIndex(tx, repository, byDigest.getDigest())) {
                        throw new RefusedException(Refusal.of(
                                Reason.UNSUPPORTED, "An index references this manifest; delete the index first"));
                    }
                    yield OciManifestStore.delete(tx, repository, byDigest.getDigest());
                }
            };
        });
        if (!deleted) {
            throw new OciException(OciErrorCode.MANIFEST_UNKNOWN, "Manifest unknown");
        }
    }

    private static Optional<OciManifestEntity> resolve(
            DSLContext tx, OciRepositoryRequest request, OciReference reference) {
        return OciRepositoryStore.find(tx, request.getRepository()).flatMap(repository -> {
            Optional<Digest> digest = switch (reference) {
                case OciReference.Tag tag -> OciManifestStore.tag(tx, repository, tag.getValue());
                case OciReference.ByDigest byDigest -> Optional.of(byDigest.getDigest());
            };
            return digest.flatMap(d -> OciManifestStore.find(tx, repository, d));
        });
    }

    private static void refuseIfPolicyBlocks(
            DSLContext tx, OciRepositoryRequest request, Namespace namespace, String version) {
        BlockStore.checkWrite(tx, OciAccessService.artifact(request, Optional.of(namespace), version))
                .ifPresent(refusal -> {
                    throw new RefusedException(refusal);
                });
    }

    private static void requireReferenced(DSLContext tx, UUID repository, OciManifest manifest) {
        for (OciDescriptor blob : manifest.getBlobs()) {
            if (!OciRepositoryStore.linked(tx, repository, blob.getDigest())) {
                throw new OciException(OciErrorCode.MANIFEST_BLOB_UNKNOWN, "Blob unknown: " + blob.getDigest());
            }
        }
        for (OciDescriptor child : manifest.getManifests()) {
            if (OciManifestStore.find(tx, repository, child.getDigest()).isEmpty()) {
                throw new OciException(OciErrorCode.MANIFEST_BLOB_UNKNOWN, "Manifest unknown: " + child.getDigest());
            }
        }
    }
}
