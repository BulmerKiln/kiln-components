/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.domain.NpmPackage;
import com.bulmerkiln.format.npm.domain.NpmPackageName;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.domain.NpmPublication;
import com.bulmerkiln.format.npm.domain.NpmPublishedVersion;
import com.bulmerkiln.format.npm.domain.NpmRoute;
import com.bulmerkiln.format.npm.entity.NpmDistTagEntity;
import com.bulmerkiln.format.npm.entity.NpmVersionEntity;
import com.bulmerkiln.format.npm.repository.NpmDistTagRepository;
import com.bulmerkiln.format.npm.repository.NpmVersionRepository;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.node.ObjectNode;

/**
 * npm packages as stored, inside the caller's transaction: each version's document beside the
 * registry's version, and the dist-tags, which point at versions of their own package.
 */
public final class NpmPackageService {

    private final PackageService packages;
    private final BlobRecordService blobs;
    private final NpmVersionRepository documents;
    private final NpmDistTagRepository tags;

    public NpmPackageService(
            PackageService packages,
            BlobRecordService blobs,
            NpmVersionRepository documents,
            NpmDistTagRepository tags) {
        this.packages = packages;
        this.blobs = blobs;
        this.documents = documents;
        this.tags = tags;
    }

    /** The package with every version, oldest first, and its dist-tags. */
    public Optional<NpmPackage> load(NpmPackageName name) {
        return packages.find(NpmFormatModule.FORMAT, name.toString()).map(this::load);
    }

    /** Creates the package on its first publish, or returns it. */
    public RegistryPackage create(NpmPackageName name, UUID namespaceId) {
        return packages.create(NpmFormatModule.FORMAT, namespaceId, name.toString(), name.toString());
    }

    /** Adds a version with its document; one that exists is a conflict (REG-3). */
    public PackageVersion addVersion(UUID packageId, String version, String document, Principal publisher) {
        PackageVersion added = packages.addVersion(packageId, version, publisher);
        documents.add(organization(), added.getId(), document);
        return added;
    }

    /**
     * Stores a published version with its tarball, stored in the blob store as {@code blob}. The
     * dist-tags the publish names for this version move to it, and the package's first version
     * becomes {@code latest}. The caller has checked blocks and tombstones.
     */
    public void publish(
            NpmPackageName name, Namespace namespace, NpmPublication publication, BlobInfo blob, Principal publisher) {
        String version = publication.getVersion();
        RegistryPackage entry = create(name, namespace.getId());
        PackageVersion stored = addVersion(
                entry.getId(),
                version,
                NpmPackumentBuilder.JSON.writeValueAsString(publication.getManifest()),
                publisher);
        addTarball(stored.getId(), publication.getFile(), blob);
        Map<String, String> distTags = new HashMap<>(load(entry).getDistTags());
        publication.getDistTags().properties().forEach(tag -> {
            if (version.equals(tag.getValue().asString())) {
                distTags.put(NpmRoute.tag(tag.getKey()), version);
            }
        });
        distTags.putIfAbsent("latest", version);
        setDistTags(entry.getId(), distTags);
    }

    /** {@code npm deprecate}: applies each version's {@code deprecated} message, and nothing else. */
    public void deprecate(NpmPackageName name, ObjectNode versions) {
        NpmPackage snapshot = load(name).orElseThrow(NpmException::notFound);
        versions.properties().forEach(sent -> {
            NpmPublishedVersion stored = snapshot.version(sent.getKey());
            ObjectNode manifest = NpmPublication.object(NpmPackumentBuilder.JSON.readTree(stored.getDocument()));
            String message = NpmPublication.text(NpmPublication.object(sent.getValue()), "deprecated");
            if (message.equals(NpmPublication.text(manifest, "deprecated"))) {
                return;
            }
            if (message.isEmpty()) {
                manifest.remove("deprecated");
            } else {
                manifest.put("deprecated", message);
            }
            setDocument(snapshot, stored, NpmPackumentBuilder.JSON.writeValueAsString(manifest));
        });
    }

    /** Records a version's tarball, stored in the blob store as {@code blob}. */
    public void addTarball(UUID versionId, String file, BlobInfo blob) {
        blobs.record(blob.getDigest(), blob.getSize());
        packages.addFile(versionId, file, blob.getDigest(), blob.getSize());
    }

    /** A version's tarball, by its file name. */
    public Optional<PackageFile> tarball(NpmPublishedVersion version, String file) {
        return packages.file(version.getId(), file);
    }

    /** Removes a version, with its document, tarball and the tags on it (REG-8). */
    public void removeVersion(NpmPublishedVersion version) {
        packages.deleteVersion(version.getId());
    }

    /** Removes a package whose versions are all gone. */
    public void removePackage(NpmPackage pkg) {
        packages.delete(pkg.getStored().getId());
    }

    /** Replaces a version's document, for {@code npm deprecate}. */
    public void setDocument(NpmPackage pkg, NpmPublishedVersion version, String document) {
        documents.setDocument(organization(), version.getId(), document);
        packages.touch(pkg.getStored().getId());
    }

    /**
     * Makes the package's dist-tags exactly {@code distTags}, each naming a version of it. A tag
     * pointing at a version the package doesn't have is dropped.
     */
    public void setDistTags(UUID packageId, Map<String, String> distTags) {
        UUID organization = organization();
        Map<String, UUID> ids = packages.versions(packageId).stream()
                .collect(Collectors.toMap(PackageVersion::getVersion, PackageVersion::getId));
        for (NpmDistTagEntity tag : tags.findAll(organization, packageId)) {
            if (!distTags.containsKey(tag.getId().getTag())) {
                tags.remove(organization, packageId, tag.getId().getTag());
            }
        }
        distTags.forEach((tag, version) -> {
            UUID id = ids.get(version);
            if (id != null) {
                tags.set(organization, packageId, tag, id);
            }
        });
        packages.touch(packageId);
    }

    private NpmPackage load(RegistryPackage pkg) {
        UUID organization = organization();
        Map<UUID, String> byId = documents.findAll(organization, pkg.getId()).stream()
                .collect(Collectors.toMap(d -> d.getId().getVersionId(), NpmVersionEntity::getDocument));
        List<NpmPublishedVersion> versions = packages.versions(pkg.getId()).stream()
                .map(v -> new NpmPublishedVersion(v, byId.getOrDefault(v.getId(), "{}")))
                .toList();
        Map<UUID, String> names = versions.stream()
                .collect(Collectors.toMap(NpmPublishedVersion::getId, NpmPublishedVersion::getVersion));
        Map<String, String> distTags = new LinkedHashMap<>();
        tags.findAll(organization, pkg.getId())
                .forEach(tag -> distTags.put(tag.getId().getTag(), names.get(tag.getVersionId())));
        return new NpmPackage(pkg, versions, new HashMap<>(distTags));
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
