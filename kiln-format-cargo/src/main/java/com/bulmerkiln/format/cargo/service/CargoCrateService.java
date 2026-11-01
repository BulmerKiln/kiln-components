/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.cargo.CargoFormatModule;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoPublishedVersion;
import com.bulmerkiln.format.cargo.entity.CargoVersionEntity;
import com.bulmerkiln.format.cargo.repository.CargoVersionRepository;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Crates as stored, inside the caller's transaction: each version's index line and publish metadata
 * beside the registry's version, and the {@code .crate} as its file. Crates are found by their
 * canonical name, so {@code acme_core} and {@code acme-core} are one crate.
 */
public final class CargoCrateService {

    private final PackageService packages;
    private final BlobRecordService blobs;
    private final CargoVersionRepository versions;

    public CargoCrateService(PackageService packages, BlobRecordService blobs, CargoVersionRepository versions) {
        this.packages = packages;
        this.blobs = blobs;
        this.versions = versions;
    }

    public Optional<RegistryPackage> find(CargoCrateName name) {
        return packages.find(CargoFormatModule.FORMAT, name.canonical());
    }

    /** Creates the crate on its first publish, under the name as first spelled, or returns it. */
    public RegistryPackage create(CargoCrateName name, UUID namespaceId) {
        return packages.create(CargoFormatModule.FORMAT, namespaceId, name.toString(), name.canonical());
    }

    /** Adds a version with its index line and publish metadata; one that exists is a conflict (REG-3). */
    public PackageVersion addVersion(
            UUID crateId, String version, String indexEntry, String publishMetadata, Principal publisher) {
        PackageVersion added = packages.addVersion(crateId, version, publisher);
        versions.add(organization(), added.getId(), indexEntry, publishMetadata);
        return added;
    }

    /** Records a version's {@code .crate}, stored in the blob store as {@code blob}. */
    public void addCrate(UUID versionId, String file, BlobInfo blob) {
        blobs.record(blob.getDigest(), blob.getSize());
        packages.addFile(versionId, file, blob.getDigest(), blob.getSize());
    }

    /** Every version of a crate, oldest first. */
    public List<CargoPublishedVersion> versions(RegistryPackage crate) {
        Map<UUID, CargoVersionEntity> rows = versions.findAll(organization(), crate.getId()).stream()
                .collect(Collectors.toMap(row -> row.getId().getVersionId(), Function.identity()));
        List<CargoPublishedVersion> published = new ArrayList<>();
        for (PackageVersion version : packages.versions(crate.getId())) {
            CargoVersionEntity row = rows.get(version.getId());
            if (row != null) {
                published.add(new CargoPublishedVersion(version, row.getIndexEntry(), row.isYanked()));
            }
        }
        return published;
    }

    public Optional<PackageFile> crate(UUID versionId, String file) {
        return packages.file(versionId, file);
    }

    public Optional<PackageVersion> version(RegistryPackage crate, String version) {
        return packages.version(crate.getId(), version);
    }

    /** Yanks or unyanks a version, which changes the crate's index file. */
    public void setYanked(RegistryPackage crate, PackageVersion version, boolean yanked) {
        versions.setYanked(organization(), version.getId(), yanked);
        packages.touch(crate.getId());
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
