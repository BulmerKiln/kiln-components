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
import com.bulmerkiln.format.maven.domain.MavenDeployedFile;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.format.maven.domain.MavenSnapshots;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
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
 * becomes visible once its POM lands. Every checksum Maven asks for is taken while the file streams
 * in and kept with its blob (FMT-MVN-12).
 */
public final class MavenFileService {

    private final Database database;
    private final MavenAccessService access;
    private final PackageService packages;
    private final BlobRecordService blobs;
    private final MavenFileStorageService storage;
    private final ArtifactEventService events;

    public MavenFileService(
            Database database,
            MavenAccessService access,
            PackageService packages,
            BlobRecordService blobs,
            MavenFileStorageService storage,
            ArtifactEventService events) {
        this.database = database;
        this.access = access;
        this.packages = packages;
        this.blobs = blobs;
        this.storage = storage;
        this.events = events;
    }

    /**
     * Stores a file, or verifies an uploaded checksum against the file it names. The same bytes
     * again are a no-op; other bytes under a stored name are {@code 409}.
     */
    public void deploy(MavenPath.ArtifactFile file, String fileName, InputStream body) throws IOException {
        Namespace namespace = access.requireToWrite(file);
        MavenChecksumAlgorithm checksum = file.getChecksum();
        if (checksum != null) {
            verifyUploadedChecksum(file, fileName, checksum, body);
            return;
        }
        Principal publisher = CurrentPrincipal.get();
        MavenDeployedFile deployed = storage.store(body);
        BlobInfo blob = deployed.getBlob();
        database.run(() -> {
            access.checkWrite(file, namespace);
            String name = MavenAccessService.name(file);
            RegistryPackage entry = packages.create(MavenFormatModule.FORMAT, namespace.getId(), name, name);
            PackageVersion version = packages.ensureVersion(entry.getId(), file.getVersion(), publisher);
            blobs.record(blob.getDigest(), blob.getSize(), deployed.getChecksums());
            packages.addFile(version.getId(), fileName, blob.getDigest(), blob.getSize());
        });
        if (isPom(file)) {
            events.pushed(MavenAccessService.artifact(file, Optional.of(namespace), file.getVersion()), blob.getSize());
        }
    }

    /** Opens a stored file; a {@code -SNAPSHOT} name resolves to the latest build of its kind. */
    public BlobRead download(MavenPath.ArtifactFile file, String fileName) throws IOException {
        Optional<Namespace> namespace = access.require(file, Operation.PULL);
        access.checkRead(file, namespace, file.getVersion());
        PackageFile stored = locate(file, fileName);
        BlobRead read = storage.open(stored.getDigest());
        if (isPom(file)) {
            events.pulled(MavenAccessService.artifact(file, namespace, file.getVersion()), read.getLength());
        }
        return read;
    }

    /** A file's checksum, for whichever algorithm is asked for (FMT-MVN-12). */
    public String checksum(MavenPath.ArtifactFile file, String fileName, MavenChecksumAlgorithm algorithm)
            throws IOException {
        Optional<Namespace> namespace = access.require(file, Operation.PULL);
        access.checkRead(file, namespace, file.getVersion());
        String target =
                fileName.substring(0, fileName.length() - algorithm.suffix().length());
        return storage.checksum(locate(file, target), algorithm);
    }

    public static boolean isPom(MavenPath.ArtifactFile file) {
        return "pom".equals(file.getExtension()) && file.getClassifier() == null && file.getChecksum() == null;
    }

    private PackageFile locate(MavenPath.ArtifactFile file, String fileName) {
        return database.transaction(() -> packages.find(MavenFormatModule.FORMAT, MavenAccessService.name(file))
                        .flatMap(entry -> packages.version(entry.getId(), file.getVersion()))
                        .flatMap(version -> MavenSnapshots.resolve(file, fileName, packages.files(version.getId()))))
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
        Optional<PackageFile> stored =
                database.transaction(() -> packages.find(MavenFormatModule.FORMAT, MavenAccessService.name(file))
                        .flatMap(entry -> packages.version(entry.getId(), file.getVersion()))
                        .flatMap(version -> packages.file(version.getId(), target)));
        if (stored.isEmpty()) {
            return;
        }
        String actual = storage.checksum(stored.get(), algorithm);
        String first = sent.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!actual.equals(first)) {
            throw new RefusedException(Refusal.of(Reason.INVALID, "The checksum doesn't match " + target));
        }
    }
}
