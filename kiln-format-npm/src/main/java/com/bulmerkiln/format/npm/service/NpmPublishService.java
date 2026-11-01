/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.dto.NpmPublishRequest;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code npm publish} and {@code npm deprecate}, which both {@code PUT} the package document. A
 * publish carries exactly one version and its tarball, checked against {@code dist.integrity} and
 * {@code dist.shasum}; a deprecate carries no tarball and changes only {@code deprecated}
 * (FMT-NPM-12, FMT-NPM-13).
 */
public final class NpmPublishService {

    private final Database database;
    private final NpmAccessService access;
    private final StorageRouter storage;
    private final ArtifactEventService events;
    private final long maxTarballSize;

    public NpmPublishService(
            Database database,
            NpmAccessService access,
            StorageRouter storage,
            ArtifactEventService events,
            long maxTarballSize) {
        this.database = database;
        this.access = access;
        this.storage = storage;
        this.events = events;
        this.maxTarballSize = maxTarballSize;
    }

    public void put(NpmPackageRequest request, InputStream body) throws IOException {
        var namespace = access.requireToWrite(request, Operation.PUSH);
        BlobStore store = storage.store(OrganizationContext.current());
        UploadId upload = store.createUpload();
        AtomicReference<NpmTarballSink> sink = new AtomicReference<>();
        try {
            // Base64 is 4/3 of the tarball; the rest of the document is small next to it.
            // A second tarball is read into nothing; the publish is refused for carrying two.
            NpmPublishRequest parsed = NpmPublishRequest.read(body, maxTarballSize * 4 / 3 + 16 * 1024 * 1024, () -> {
                if (sink.get() != null) {
                    return OutputStream.nullOutputStream();
                }
                sink.set(new NpmTarballSink(store, upload, maxTarballSize));
                return sink.get();
            });
            requireName(request, parsed);
            NpmTarballSink tarball = sink.get();
            if (tarball == null) {
                store.abortUpload(upload);
                NpmPublication.deprecate(database, request, parsed.getVersions());
                return;
            }
            NpmPublication publication = NpmPublication.of(request, parsed);
            NpmPublication.verify(publication, tarball);
            BlobInfo blob = store.commit(upload, tarball.digest());
            database.run(tx -> publication.store(tx, request, namespace, blob));
            events.pushed(
                    NpmAccessService.artifact(request, Optional.of(namespace), publication.getVersion()),
                    blob.getSize());
        } catch (RuntimeException | IOException e) {
            store.abortUpload(upload);
            throw e;
        }
    }

    private static void requireName(NpmPackageRequest request, NpmPublishRequest parsed) {
        if (!request.getName().toString().equals(parsed.getName())) {
            throw new NpmException(Reason.INVALID, "The package name doesn't match the URL");
        }
    }

    /** A version's manifest field, or an empty string. */
    static String text(JsonNode manifest, String field) {
        JsonNode value = manifest.get(field);
        return value == null || !value.isString() ? "" : value.asString();
    }

    static ObjectNode object(JsonNode node) {
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw new NpmException(Reason.INVALID, "A version must be an object");
    }
}
