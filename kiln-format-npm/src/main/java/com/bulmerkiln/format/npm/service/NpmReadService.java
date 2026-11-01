/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.domain.NpmVersion;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.entity.NpmPackageEntity;
import com.bulmerkiln.format.npm.store.NpmPackageStore;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.PackageStore;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.StorageRouter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reading packages: packuments, single versions, tarballs and dist-tags (FMT-NPM-10, FMT-NPM-11).
 * Versions a block refuses are left out of packuments, so clients stop resolving them; asking for
 * one directly is {@code 403}.
 */
public final class NpmReadService {

    private final Database database;
    private final NpmAccessService access;
    private final StorageRouter storage;
    private final ArtifactEventService events;

    public NpmReadService(
            Database database, NpmAccessService access, StorageRouter storage, ArtifactEventService events) {
        this.database = database;
        this.access = access;
        this.storage = storage;
        this.events = events;
    }

    public String packument(NpmPackageRequest request, boolean abbreviated) {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        refuseIfBlocked(request, namespace, null);
        NpmPackageEntity snapshot = load(request);
        List<PackageVersionEntity> readable = snapshot.getVersions().stream()
                .filter(v ->
                        access.readRefusal(request, namespace, v.getVersion()).isEmpty())
                .toList();
        Map<String, String> tags = snapshot.readableTags(readable);
        return abbreviated
                ? NpmPackumentBuilder.abbreviated(request, tags, readable)
                : NpmPackumentBuilder.full(request, tags, readable);
    }

    public String version(NpmPackageRequest request, NpmVersion version) {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        PackageVersionEntity found = load(request).version(version.getValue());
        refuseIfBlocked(request, namespace, found.getVersion());
        return NpmPackumentBuilder.JSON.writeValueAsString(NpmPackumentBuilder.manifest(request, found));
    }

    /** Opens a tarball, refusing it when its version is blocked; the download counts as a pull. */
    public BlobRead tarball(NpmPackageRequest request, String file) throws IOException {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        NpmPackageEntity snapshot = load(request);
        PackageVersionEntity version = snapshot.getVersions().stream()
                .filter(v -> request.tarballFile(v.getVersion()).equals(file))
                .findFirst()
                .orElseThrow(NpmException::notFound);
        refuseIfBlocked(request, namespace, version.getVersion());
        PackageFileEntity tarball = database.transaction(tx -> PackageStore.file(tx, version.getId(), file))
                .orElseThrow(NpmException::notFound);
        BlobRead read = storage.store(OrganizationContext.current())
                .read(tarball.getDigest(), null)
                .orElseThrow(NpmException::notFound);
        events.pulled(NpmAccessService.artifact(request, namespace, version.getVersion()), read.getLength());
        return read;
    }

    public Map<String, String> distTags(NpmPackageRequest request) {
        Optional<Namespace> namespace = access.require(request, Operation.PULL);
        refuseIfBlocked(request, namespace, null);
        return load(request).getDistTags();
    }

    private NpmPackageEntity load(NpmPackageRequest request) {
        return database.transaction(tx -> NpmPackageStore.load(tx, request.getName()))
                .orElseThrow(NpmException::notFound);
    }

    private void refuseIfBlocked(NpmPackageRequest request, Optional<Namespace> namespace, @Nullable String version) {
        access.readRefusal(request, namespace, version).ifPresent(refusal -> {
            throw new RefusedException(refusal);
        });
    }
}
