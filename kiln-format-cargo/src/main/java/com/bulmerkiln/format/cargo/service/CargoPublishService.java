/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.CargoFormatModule;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.format.cargo.dto.CargoPublishRequest;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.BlobRecordStore;
import com.bulmerkiln.registry.store.BlockStore;
import com.bulmerkiln.registry.store.PackageStore;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * {@code cargo publish}: the length-prefixed metadata and {@code .crate}, stored with the index line
 * that describes it (FMT-CARGO-10). Versions never change, and a name can't be taken again under
 * another spelling.
 */
public final class CargoPublishService {

    private final Database database;
    private final CargoAccessService access;
    private final StorageRouter storage;
    private final ArtifactEventService events;
    private final long maxCrateSize;

    public CargoPublishService(
            Database database,
            CargoAccessService access,
            StorageRouter storage,
            ArtifactEventService events,
            long maxCrateSize) {
        this.database = database;
        this.access = access;
        this.storage = storage;
        this.events = events;
        this.maxCrateSize = maxCrateSize;
    }

    public void publish(InputStream body) throws IOException {
        long limit = CargoPublishRequest.DEFAULT_MAX_METADATA + maxCrateSize + 2 * Integer.BYTES;
        byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
        if (bytes.length > limit) {
            throw new CargoException(Reason.TOO_LARGE, "Crates are limited to " + maxCrateSize + " bytes");
        }
        CargoPublishRequest parsed =
                CargoPublishRequest.parse(bytes, CargoPublishRequest.DEFAULT_MAX_METADATA, maxCrateSize);
        byte[] crate = parsed.crate();
        Digest digest = Digest.sha256(crate);
        CargoIndexEntry entry = CargoIndexEntry.fromPublish(parsed.metadataJson(), digest.encoded());
        CargoCrateName name = new CargoCrateName(entry.name());
        Namespace namespace = access.requireToWrite(name, Operation.PUSH);
        BlobInfo blob = store(crate, digest);
        database.run(tx -> {
            BlockStore.checkWrite(tx, CargoAccessService.artifact(name, Optional.of(namespace), entry.version()))
                    .ifPresent(refusal -> {
                        throw new RefusedException(refusal);
                    });
            Optional<PackageEntity> existing = PackageStore.find(tx, CargoFormatModule.FORMAT, name.canonical());
            if (existing.isPresent() && !existing.get().name().equals(name.toString())) {
                throw new CargoException(
                        Reason.CONFLICT,
                        "The name is taken as " + existing.get().name());
            }
            PackageEntity pkg = PackageStore.create(
                    tx, CargoFormatModule.FORMAT, namespace.id(), name.toString(), name.canonical());
            PackageVersionEntity version = PackageStore.addVersion(
                    tx, pkg.id(), entry.version(), CargoIndexEntry.JSON.writeValueAsString(entry.json()));
            BlobRecordStore.record(tx, blob.digest(), blob.size());
            PackageStore.addFile(tx, version.id(), name + "-" + entry.version() + ".crate", blob.digest(), blob.size());
        });
        events.pushed(CargoAccessService.artifact(name, Optional.of(namespace), entry.version()), blob.size());
    }

    private BlobInfo store(byte[] crate, Digest digest) throws IOException {
        BlobStore store = storage.store(OrganizationContext.current());
        UploadId upload = store.createUpload();
        try {
            store.append(upload, 0, new ByteArrayInputStream(crate), maxCrateSize);
            return store.commit(upload, digest);
        } catch (IOException | RuntimeException e) {
            store.abortUpload(upload);
            throw e;
        }
    }
}
