/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.service;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.Publisher;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.mapper.PackageMapper;
import com.bulmerkiln.registry.repository.PackageFileRepository;
import com.bulmerkiln.registry.repository.PackageRepository;
import com.bulmerkiln.registry.repository.PackageVersionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Packages of every format, their versions and each version's files. Versions and files are
 * immutable once written (REG-3). Versions are found by the format's normalized form, which for
 * these formats is the version as published. Adding a version or file, or deleting a version,
 * marks the package as changed; a format marks changes of its own with {@link #touch}.
 */
public final class PackageService {

    private final PackageRepository packages;
    private final PackageVersionRepository versions;
    private final PackageFileRepository files;
    private final PackageMapper mapper;

    public PackageService(
            PackageRepository packages,
            PackageVersionRepository versions,
            PackageFileRepository files,
            PackageMapper mapper) {
        this.packages = packages;
        this.versions = versions;
        this.files = files;
        this.mapper = mapper;
    }

    /** The package whose normalized name is {@code normalizedName}, as each format normalizes. */
    public Optional<RegistryPackage> find(ArtifactFormat format, String normalizedName) {
        return packages.findByName(organization(), format.getId(), normalizedName)
                .map(mapper::toPackage);
    }

    /**
     * Creates the package, or returns the one already there: two first publishes racing each other
     * both see the same package.
     */
    public RegistryPackage create(ArtifactFormat format, UUID namespaceId, String name, String normalizedName) {
        packages.addIfAbsent(organization(), UUID.randomUUID(), namespaceId, format.getId(), name, normalizedName);
        return find(format, normalizedName).orElseThrow();
    }

    /** Marks a change the format shows, such as a moved tag or a yank. */
    public void touch(UUID packageId) {
        packages.touch(organization(), packageId);
    }

    /** Removes a package whose versions are all deleted (REG-8). */
    public void delete(UUID packageId) {
        packages.remove(organization(), packageId);
    }

    /** Every version of a package, oldest first. */
    public List<PackageVersion> versions(UUID packageId) {
        return versions.findAll(organization(), packageId).stream()
                .map(mapper::toVersion)
                .toList();
    }

    public Optional<PackageVersion> version(UUID packageId, String version) {
        return versions.findByVersion(organization(), packageId, version).map(mapper::toVersion);
    }

    public Optional<PackageVersion> versionById(UUID versionId) {
        return versions.findOne(organization(), versionId).map(mapper::toVersion);
    }

    /** Adds a version; one that already exists is a conflict, since versions never change (REG-3). */
    public PackageVersion addVersion(UUID packageId, String version, Principal publisher) {
        if (!add(packageId, version, publisher)) {
            throw new RefusedException(
                    Refusal.of(Reason.CONFLICT, "Version " + version + " already exists and can't be replaced"));
        }
        return version(packageId, version).orElseThrow();
    }

    /**
     * The version, created if it doesn't exist yet: for formats that publish a version as many
     * separate files, such as Maven, where each file is immutable but the version grows (REG-3).
     */
    public PackageVersion ensureVersion(UUID packageId, String version, Principal publisher) {
        add(packageId, version, publisher);
        return version(packageId, version).orElseThrow();
    }

    /**
     * Removes a version and its files, for an unpublish. The caller tombstones the version first,
     * so it can't come back (REG-8). The blobs stay recorded until storage collects them.
     */
    public void deleteVersion(UUID versionId) {
        UUID organization = organization();
        packages.touchForVersion(organization, versionId);
        versions.remove(organization, versionId);
    }

    /**
     * Adds a file to a version. The same bytes again is a no-op, since clients retry; different
     * bytes under a name that exists is a conflict (REG-3). The blob must already be recorded.
     */
    public PackageFile addFile(UUID versionId, String name, Digest digest, long size) {
        UUID organization = organization();
        if (files.addIfAbsent(organization, UUID.randomUUID(), versionId, name, digest.toString(), size) > 0) {
            packages.touchForVersion(organization, versionId);
        }
        PackageFile stored = file(versionId, name).orElseThrow();
        if (!stored.getDigest().equals(digest)) {
            throw new RefusedException(
                    Refusal.of(Reason.CONFLICT, "File " + name + " already exists and can't be replaced"));
        }
        return stored;
    }

    public Optional<PackageFile> file(UUID versionId, String name) {
        return files.findByName(organization(), versionId, name).map(mapper::toFile);
    }

    public Optional<PackageFile> fileById(UUID fileId) {
        return files.findOne(organization(), fileId).map(mapper::toFile);
    }

    public List<PackageFile> files(UUID versionId) {
        return files.findAll(organization(), versionId).stream()
                .map(mapper::toFile)
                .toList();
    }

    private boolean add(UUID packageId, String version, Principal publisher) {
        Optional<Publisher> by = Publisher.of(publisher);
        UUID organization = organization();
        int added = versions.addIfAbsent(
                organization,
                UUID.randomUUID(),
                packageId,
                version,
                version,
                by.map(Publisher::getKind).orElse(null),
                by.map(Publisher::getId).orElse(null));
        if (added > 0) {
            packages.touch(organization, packageId);
        }
        return added > 0;
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
