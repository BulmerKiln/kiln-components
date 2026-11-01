/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.domain.OciDescriptor;
import com.bulmerkiln.format.oci.domain.OciManifest;
import com.bulmerkiln.format.oci.domain.OciManifestParser;
import com.bulmerkiln.format.oci.domain.OciManifestRecord;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.domain.OciStoredManifest;
import com.bulmerkiln.format.oci.dto.OciManifestPushResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pulling, pushing and deleting manifests (FMT-OCI-17 to FMT-OCI-20). A manifest's bytes are a blob,
 * written before the manifest is recorded, as a layer's are; a manifest never recorded leaves a
 * blob for storage to collect.
 */
public final class OciManifestService {

    private final Database database;
    private final OciAccessService access;
    private final OciRepositoryService repositories;
    private final OciManifestRecordService manifests;
    private final StorageRouter storage;
    private final ArtifactEventService events;
    private final OciManifestParser parser;

    public OciManifestService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests,
            StorageRouter storage,
            ArtifactEventService events,
            int maxManifestSize) {
        this.database = database;
        this.access = access;
        this.repositories = repositories;
        this.manifests = manifests;
        this.storage = storage;
        this.events = events;
        this.parser = new OciManifestParser(maxManifestSize);
    }

    /** A manifest by tag or digest, exactly as pushed. Blocks apply to the tag and the digest. */
    public OciStoredManifest pull(OciRepositoryRequest request, OciReference reference, boolean head)
            throws IOException {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        OciManifestRecord manifest = database.transaction(() -> repositories
                        .find(request.getRepository())
                        .flatMap(repository -> manifests.find(repository, reference)))
                .orElseThrow(OciManifestService::unknown);
        access.checkRead(request, namespace, reference.toString());
        access.checkRead(request, namespace, manifest.getDigest().toString());
        // The size recorded with the manifest bounds the read, so a changed blob is refused.
        byte[] content = storage.current()
                .readBytes(manifest.getDigest(), Math.toIntExact(manifest.getSize()))
                .orElseThrow(OciManifestService::unknown);
        if (!head) {
            events.pulled(OciAccessService.artifact(request, namespace, reference.toString()), content.length);
        }
        return new OciStoredManifest(manifest, content);
    }

    /**
     * Stores a manifest and, for a tag, points the tag at it. Everything it references must already
     * be in the repository, except a subject, which OCI 1.1 lets be pushed later.
     */
    public OciManifestPushResponse push(
            OciRepositoryRequest request, OciReference reference, @Nullable String contentType, byte[] content)
            throws IOException {
        Namespace namespace = access.requireToWrite(request, Operation.PUSH);
        OciManifest manifest = parser.parse(content, contentType);
        if (reference.contradicts(manifest.getDigest())) {
            throw new OciException(OciErrorCode.DIGEST_INVALID, "The manifest doesn't match its digest");
        }
        BlobInfo stored = storage.current().write(manifest.getDigest(), content);
        database.run(() -> {
            access.checkWrite(request, namespace, reference.toString());
            access.checkWrite(request, namespace, manifest.getDigest().toString());
            UUID repository = repositories.ensure(request.getRepository(), namespace);
            UUID version = manifests.store(repository, manifest, stored);
            String tag = reference.tag();
            if (tag != null) {
                manifests.setTag(repository, tag, version);
            }
        });
        events.pushed(OciAccessService.artifact(request, Optional.of(namespace), reference.toString()), content.length);
        OciDescriptor subject = manifest.getSubject();
        return new OciManifestPushResponse(manifest.getDigest(), subject == null ? null : subject.getDigest());
    }

    private static OciException unknown() {
        return new OciException(OciErrorCode.MANIFEST_UNKNOWN, "Manifest unknown");
    }
}
