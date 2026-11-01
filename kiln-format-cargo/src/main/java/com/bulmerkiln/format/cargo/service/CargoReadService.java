/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.CargoFormatModule;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.format.cargo.dto.CargoIndexFileResponse;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.PackageStore;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.StorageRouter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Index files, downloads and yanks (FMT-CARGO-11, FMT-CARGO-12). A yanked version stays in the
 * index and downloadable, so existing lockfiles keep working; a blocked one leaves the index and is
 * {@code 403}.
 */
public final class CargoReadService {

    private final Database database;
    private final CargoAccessService access;
    private final StorageRouter storage;
    private final ArtifactEventService events;

    public CargoReadService(
            Database database, CargoAccessService access, StorageRouter storage, ArtifactEventService events) {
        this.database = database;
        this.access = access;
        this.storage = storage;
        this.events = events;
    }

    public CargoIndexFileResponse index(CargoCrateName name) {
        Optional<Namespace> namespace = access.require(name, Operation.PULL);
        refuseIfBlocked(name, namespace, null);
        List<PackageVersionEntity> versions =
                database.transaction(tx -> PackageStore.find(tx, CargoFormatModule.FORMAT, name.canonical())
                        .map(entry -> PackageStore.versions(tx, entry.getId()))
                        .orElseThrow(CargoReadService::notFound));
        StringBuilder body = new StringBuilder();
        Instant modified = Instant.EPOCH;
        for (PackageVersionEntity version : versions) {
            if (access.readRefusal(name, namespace, version.getVersion()).isPresent()) {
                continue;
            }
            ObjectNode line = (ObjectNode) CargoIndexEntry.JSON.readTree(version.getMetadata());
            line.put("yanked", version.isYanked());
            body.append(CargoIndexEntry.JSON.writeValueAsString(line)).append('\n');
            modified = version.getPublishedAt().isAfter(modified) ? version.getPublishedAt() : modified;
        }
        if (body.isEmpty()) {
            throw notFound();
        }
        return new CargoIndexFileResponse(body.toString(), modified);
    }

    public BlobRead download(CargoCrateName name, String version) throws IOException {
        Optional<Namespace> namespace = access.require(name, Operation.PULL);
        refuseIfBlocked(name, namespace, version);
        PackageFileEntity crate = database.transaction(
                        tx -> PackageStore.find(tx, CargoFormatModule.FORMAT, name.canonical())
                                .flatMap(entry -> PackageStore.version(tx, entry.getId(), version))
                                .flatMap(v -> PackageStore.file(tx, v.getId(), name + "-" + version + ".crate")))
                .orElseThrow(CargoReadService::notFound);
        BlobRead read = storage.store(OrganizationContext.current())
                .read(crate.getDigest(), null)
                .orElseThrow(CargoReadService::notFound);
        events.pulled(CargoAccessService.artifact(name, namespace, version), read.getLength());
        return read;
    }

    /** {@code cargo yank} and {@code --undo}: marks the version, which stays downloadable. */
    public void yank(CargoCrateName name, String version, boolean yanked) {
        access.requireToWrite(name, Operation.PUSH);
        database.run(tx -> {
            PackageVersionEntity found = PackageStore.find(tx, CargoFormatModule.FORMAT, name.canonical())
                    .flatMap(entry -> PackageStore.version(tx, entry.getId(), version))
                    .orElseThrow(CargoReadService::notFound);
            PackageStore.setYanked(tx, found.getId(), yanked);
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
