/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.format.maven.domain.MavenSnapshots;
import com.bulmerkiln.format.maven.store.MavenFileStore;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.BlobRecordStore;
import com.bulmerkiln.registry.store.BlockStore;
import com.bulmerkiln.registry.store.PackageStore;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Deploying and downloading a version's files, and their checksums (FMT-MVN-10 to FMT-MVN-13). A
 * deploy is many independent {@code PUT}s; each file is immutable once stored, and the version
 * becomes visible once its POM lands.
 */
public final class MavenFileService {

    private final Database database;
    private final MavenAccessService access;
    private final MavenFileStore blobs;
    private final ArtifactEventService events;

    public MavenFileService(
            Database database, MavenAccessService access, MavenFileStore blobs, ArtifactEventService events) {
        this.database = database;
        this.access = access;
        this.blobs = blobs;
        this.events = events;
    }

    /**
     * Stores a file, or verifies an uploaded checksum against the file it names. The same bytes
     * again are a no-op; other bytes under a stored name are {@code 409}.
     */
    public void deploy(MavenPath.ArtifactFile file, String fileName, InputStream body) throws IOException {
        Namespace namespace = access.requireToWrite(file);
        MavenChecksumAlgorithm checksum = file.checksum();
        if (checksum != null) {
            verifyUploadedChecksum(file, fileName, checksum, body);
            return;
        }
        BlobInfo blob = blobs.store(body);
        database.run(tx -> {
            BlockStore.checkWrite(tx, MavenAccessService.artifact(file, Optional.of(namespace), file.version()))
                    .ifPresent(refusal -> {
                        throw new RefusedException(refusal);
                    });
            String name = MavenAccessService.name(file);
            PackageEntity entry = PackageStore.create(tx, MavenFormatModule.FORMAT, namespace.id(), name, name);
            PackageVersionEntity version = PackageStore.ensureVersion(tx, entry.id(), file.version());
            BlobRecordStore.record(tx, blob.digest(), blob.size());
            PackageStore.addFile(tx, version.id(), fileName, blob.digest(), blob.size());
        });
        if (isPom(file)) {
            events.pushed(MavenAccessService.artifact(file, Optional.of(namespace), file.version()), blob.size());
        }
    }

    /** Opens a stored file; a {@code -SNAPSHOT} name resolves to the latest build of its kind. */
    public BlobRead download(MavenPath.ArtifactFile file, String fileName) throws IOException {
        Optional<Namespace> namespace = access.require(file, Operation.PULL);
        access.checkRead(file, namespace, file.version());
        PackageFileEntity stored = locate(file, fileName);
        BlobRead read = blobs.open(stored.digest());
        if (isPom(file)) {
            events.pulled(MavenAccessService.artifact(file, namespace, file.version()), read.length());
        }
        return read;
    }

    /** A file's checksum, computed on the server for whichever algorithm is asked for (FMT-MVN-12). */
    public String checksum(MavenPath.ArtifactFile file, String fileName, MavenChecksumAlgorithm algorithm)
            throws IOException {
        Optional<Namespace> namespace = access.require(file, Operation.PULL);
        access.checkRead(file, namespace, file.version());
        String target =
                fileName.substring(0, fileName.length() - algorithm.suffix().length());
        return blobs.checksum(locate(file, target).digest(), algorithm);
    }

    private PackageFileEntity locate(MavenPath.ArtifactFile file, String fileName) {
        return database.transaction(tx -> PackageStore.find(tx, MavenFormatModule.FORMAT, MavenAccessService.name(file))
                        .flatMap(entry -> PackageStore.version(tx, entry.id(), file.version()))
                        .flatMap(version ->
                                MavenSnapshots.resolve(file, fileName, PackageStore.files(tx, version.id()))))
                .orElseThrow(() -> new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found")));
    }

    /**
     * An uploaded checksum must match the file it names when that file is stored; one that arrives
     * first is accepted, since checksums are always computed here anyway.
     */
    private void verifyUploadedChecksum(
            MavenPath.ArtifactFile file, String fileName, MavenChecksumAlgorithm algorithm, InputStream body)
            throws IOException {
        String sent = new String(body.readNBytes(1024), StandardCharsets.US_ASCII).strip();
        String target =
                fileName.substring(0, fileName.length() - algorithm.suffix().length());
        Optional<PackageFileEntity> stored = database.transaction(
                tx -> PackageStore.find(tx, MavenFormatModule.FORMAT, MavenAccessService.name(file))
                        .flatMap(entry -> PackageStore.version(tx, entry.id(), file.version()))
                        .flatMap(version -> PackageStore.file(tx, version.id(), target)));
        if (stored.isEmpty()) {
            return;
        }
        String actual = blobs.checksum(stored.get().digest(), algorithm);
        String first = sent.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!actual.equals(first)) {
            throw new RefusedException(Refusal.of(Reason.INVALID, "The checksum doesn't match " + target));
        }
    }

    public static boolean isPom(MavenPath.ArtifactFile file) {
        return "pom".equals(file.extension()) && file.classifier() == null && file.checksum() == null;
    }
}
