/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.format.cargo.domain.CargoPublishedVersion;
import com.bulmerkiln.format.cargo.dto.CargoIndexFileResponse;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.StorageRouter;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Index files, downloads and yanks (FMT-CARGO-11, FMT-CARGO-12). A yanked version stays in the
 * index and downloadable, so existing lockfiles keep working; a blocked one leaves the index and is
 * {@code 403}.
 */
public final class CargoReadService {

    /** The index's {@code pubtime}: UTC, to the second, as the index format asks. */
    private static final DateTimeFormatter PUBTIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final Database database;
    private final CargoAccessService access;
    private final CargoCrateService crates;
    private final StorageRouter storage;
    private final ArtifactEventService events;

    public CargoReadService(
            Database database,
            CargoAccessService access,
            CargoCrateService crates,
            StorageRouter storage,
            ArtifactEventService events) {
        this.database = database;
        this.access = access;
        this.crates = crates;
        this.storage = storage;
        this.events = events;
    }

    public CargoIndexFileResponse index(CargoCrateName name) {
        Optional<Namespace> namespace = access.require(name, Operation.PULL);
        refuseIfBlocked(name, namespace, null);
        RegistryPackage crate = database.transaction(() -> crates.find(name)).orElseThrow(CargoReadService::notFound);
        List<CargoPublishedVersion> versions = database.transaction(() -> crates.versions(crate));
        StringBuilder body = new StringBuilder();
        for (CargoPublishedVersion version : versions) {
            if (access.readRefusal(name, namespace, version.getVersion()).isPresent()) {
                continue;
            }
            ObjectNode line = (ObjectNode) CargoIndexEntry.JSON.readTree(version.getIndexEntry());
            line.put("yanked", version.isYanked());
            line.put("pubtime", PUBTIME.format(version.getPublishedAt()));
            body.append(CargoIndexEntry.JSON.writeValueAsString(line)).append('\n');
        }
        if (body.isEmpty()) {
            throw notFound();
        }
        // A yank changes the file without a new version, so it's the crate's last change that counts.
        return new CargoIndexFileResponse(body.toString(), crate.getUpdatedAt());
    }

    public BlobRead download(CargoCrateName name, String version) throws IOException {
        Optional<Namespace> namespace = access.require(name, Operation.PULL);
        refuseIfBlocked(name, namespace, version);
        PackageFile crate = database.transaction(() -> crates.find(name)
                        .flatMap(entry -> crates.version(entry, version))
                        .flatMap(v -> crates.crate(v.getId(), name + "-" + version + ".crate")))
                .orElseThrow(CargoReadService::notFound);
        BlobRead read = storage.current().read(crate.getDigest(), null).orElseThrow(CargoReadService::notFound);
        events.pulled(CargoAccessService.artifact(name, namespace, version), read.getLength());
        return read;
    }

    /** {@code cargo yank} and {@code --undo}: marks the version, which stays downloadable. */
    public void yank(CargoCrateName name, String version, boolean yanked) {
        access.requireToWrite(name, Operation.PUSH);
        database.run(() -> {
            RegistryPackage crate = crates.find(name).orElseThrow(CargoReadService::notFound);
            PackageVersion found = crates.version(crate, version).orElseThrow(CargoReadService::notFound);
            crates.setYanked(crate, found, yanked);
        });
    }

    private void refuseIfBlocked(CargoCrateName name, Optional<Namespace> namespace, @Nullable String version) {
        access.readRefusal(name, namespace, version).ifPresent(refusal -> {
            throw new RefusedException(refusal);
        });
    }

    static CargoException notFound() {
        return new CargoException(Reason.NOT_FOUND, "Not found");
    }
}
