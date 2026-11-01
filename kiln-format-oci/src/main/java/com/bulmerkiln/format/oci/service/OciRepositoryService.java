/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.oci.OciFormatModule;
import com.bulmerkiln.format.oci.domain.OciRepositoryName;
import com.bulmerkiln.format.oci.repository.OciRepositoryBlobRepository;
import com.bulmerkiln.format.oci.repository.OciUploadSessionRepository;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.PackageService;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositories, the blobs linked to each, and their upload sessions, inside the caller's transaction.
 * A repository is a registry package of the OCI format, named as clients address it.
 */
public final class OciRepositoryService {

    private final PackageService packages;
    private final OciRepositoryBlobRepository blobs;
    private final OciUploadSessionRepository uploads;

    public OciRepositoryService(
            PackageService packages, OciRepositoryBlobRepository blobs, OciUploadSessionRepository uploads) {
        this.packages = packages;
        this.blobs = blobs;
        this.uploads = uploads;
    }

    public Optional<UUID> find(OciRepositoryName name) {
        return packages.find(OciFormatModule.FORMAT, name.getValue()).map(RegistryPackage::getId);
    }

    /** The repository, created on first push in the namespace that owns its name. */
    public UUID ensure(OciRepositoryName name, Namespace namespace) {
        return packages.create(OciFormatModule.FORMAT, namespace.getId(), name.getValue(), name.getValue())
                .getId();
    }

    /** Lets the repository read a blob the organization holds. Linking twice is harmless. */
    public void link(UUID repository, Digest digest) {
        blobs.link(organization(), repository, digest.toString());
    }

    public boolean linked(UUID repository, Digest digest) {
        return blobs.linked(organization(), repository, digest.toString());
    }

    public void startUpload(UUID repository, UUID session) {
        uploads.start(organization(), session, repository);
    }

    /** Whether {@code session} was started through {@code repository}. */
    public boolean uploadBelongs(UUID repository, UUID session) {
        return uploads.belongs(organization(), session, repository);
    }

    public void endUpload(UUID session) {
        uploads.end(organization(), session);
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
