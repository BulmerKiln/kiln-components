/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.format.cargo.dto.CargoPublishRequest;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@code cargo publish}: the length-prefixed metadata and {@code .crate}, stored with the index line
 * that describes it (FMT-CARGO-10). Versions never change, and a name can't be taken again under
 * another spelling.
 */
public final class CargoPublishService {

    private final Database database;
    private final CargoAccessService access;
    private final CargoCrateService crates;
    private final StorageRouter storage;
    private final ArtifactEventService events;
    private final long maxCrateSize;

    public CargoPublishService(
            Database database,
            CargoAccessService access,
            CargoCrateService crates,
            StorageRouter storage,
            ArtifactEventService events,
            long maxCrateSize) {
        this.database = database;
        this.access = access;
        this.crates = crates;
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
        CargoIndexEntry entry = CargoIndexEntry.fromPublish(parsed.metadataJson(), digest.getEncoded());
        CargoCrateName name = new CargoCrateName(entry.getName());
        Namespace namespace = access.requireToWrite(name, Operation.PUSH);
        BlobInfo blob = storage.current().write(digest, crate);
        Principal publisher = CurrentPrincipal.get();
        String metadata = new String(parsed.metadataJson(), StandardCharsets.UTF_8);
        database.run(() -> {
            access.checkWrite(name, namespace, entry.getVersion());
            Optional<RegistryPackage> existing = crates.find(name);
            if (existing.isPresent() && !existing.get().getName().equals(name.toString())) {
                throw new CargoException(
                        Reason.CONFLICT,
                        "The name is taken as " + existing.get().getName());
            }
            RegistryPackage pkg = crates.create(name, namespace.getId());
            PackageVersion version = crates.addVersion(
                    pkg.getId(),
                    entry.getVersion(),
                    CargoIndexEntry.JSON.writeValueAsString(entry.getJson()),
                    metadata,
                    publisher);
            crates.addCrate(version.getId(), name + "-" + entry.getVersion() + ".crate", blob);
        });
        events.pushed(CargoAccessService.artifact(name, Optional.of(namespace), entry.getVersion()), blob.getSize());
    }
}
