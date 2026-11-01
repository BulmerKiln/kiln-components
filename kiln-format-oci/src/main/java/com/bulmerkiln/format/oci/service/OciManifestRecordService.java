/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.domain.OciDescriptor;
import com.bulmerkiln.format.oci.domain.OciManifest;
import com.bulmerkiln.format.oci.domain.OciManifestRecord;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import com.bulmerkiln.format.oci.repository.OciManifestRefRepository;
import com.bulmerkiln.format.oci.repository.OciManifestRepository;
import com.bulmerkiln.format.oci.repository.OciRepositoryBlobRepository;
import com.bulmerkiln.format.oci.repository.OciTagRepository;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Manifests, what they reference, and tags, inside the caller's transaction. A manifest is a
 * version of its repository, named by its digest, whose one file is its bytes in the blob store.
 */
public final class OciManifestRecordService {

    /** The name of the file holding a manifest's bytes. */
    static final String MANIFEST_FILE = "manifest";

    private final PackageService packages;
    private final BlobRecordService blobs;
    private final OciManifestRepository manifests;
    private final OciManifestRefRepository refs;
    private final OciTagRepository tags;
    private final OciRepositoryBlobRepository links;

    public OciManifestRecordService(
            PackageService packages,
            BlobRecordService blobs,
            OciManifestRepository manifests,
            OciManifestRefRepository refs,
            OciTagRepository tags,
            OciRepositoryBlobRepository links) {
        this.packages = packages;
        this.blobs = blobs;
        this.manifests = manifests;
        this.refs = refs;
        this.tags = tags;
        this.links = links;
    }

    /**
     * Records a manifest whose bytes are {@code stored}, and what it references, returning its
     * version, published by the caller. Everything it references must already be in the repository,
     * except a subject, which OCI 1.1 lets be pushed later. Recording the same manifest again is
     * harmless.
     */
    public UUID store(UUID repository, OciManifest manifest, BlobInfo stored) {
        requireReferenced(repository, manifest);
        Principal publisher = CurrentPrincipal.get();
        blobs.record(stored.getDigest(), stored.getSize());
        PackageVersion version =
                packages.ensureVersion(repository, manifest.getDigest().toString(), publisher);
        PackageFile file = packages.addFile(version.getId(), MANIFEST_FILE, stored.getDigest(), stored.getSize());
        OciDescriptor subject = manifest.getSubject();
        UUID organization = organization();
        int added = manifests.addIfAbsent(
                organization,
                repository,
                version.getId(),
                manifest.getMediaType(),
                artifactType(manifest),
                subject == null ? null : subject.getDigest().toString(),
                file.getId());
        if (added > 0) {
            for (OciDescriptor blob : manifest.getBlobs()) {
                refs.addIfAbsent(organization, version.getId(), blob.getDigest().toString(), "blob");
            }
            for (OciDescriptor child : manifest.getManifests()) {
                refs.addIfAbsent(
                        organization, version.getId(), child.getDigest().toString(), "manifest");
            }
        }
        return version.getId();
    }

    public Optional<OciManifestRecord> find(UUID repository, Digest digest) {
        return packages.version(repository, digest.toString()).flatMap(version -> record(version, digest));
    }

    /** The manifest a tag points at, or the one a digest names. */
    public Optional<OciManifestRecord> find(UUID repository, OciReference reference) {
        Digest digest = reference.digest();
        String tag = reference.tag();
        Optional<Digest> named =
                digest != null ? Optional.of(digest) : tag == null ? Optional.empty() : tag(repository, tag);
        return named.flatMap(d -> find(repository, d));
    }

    /** The digest of the manifest {@code tag} points at. */
    public Optional<Digest> tag(UUID repository, String tag) {
        return tags.findDigest(organization(), repository, tag).map(Digest::parse);
    }

    /** Points a tag at a manifest version, moving it if it pointed elsewhere. */
    public void setTag(UUID repository, String tag, UUID version) {
        tags.set(organization(), repository, tag, version);
        packages.touch(repository);
    }

    public boolean deleteTag(UUID repository, String tag) {
        boolean deleted = tags.remove(organization(), repository, tag) > 0;
        if (deleted) {
            packages.touch(repository);
        }
        return deleted;
    }

    /** Whether another manifest in the repository, an index, references this one. */
    public boolean referencedByIndex(UUID repository, Digest digest) {
        return manifests.referencedByIndex(organization(), repository, digest.toString());
    }

    /**
     * Deletes a manifest, with the tags pointing at it and what it references. Its blob stays
     * recorded until storage collects it.
     */
    public boolean delete(UUID repository, Digest digest) {
        Optional<PackageVersion> version = packages.version(repository, digest.toString());
        version.ifPresent(found -> packages.deleteVersion(found.getId()));
        return version.isPresent();
    }

    /** Up to {@code limit} tags after {@code last}, in lexical order. */
    public List<String> tags(UUID repository, @Nullable String last, int limit) {
        return tags.findNames(organization(), repository, last == null ? "" : last, limit);
    }

    /** The manifests whose subject is {@code digest}, optionally only those of one artifact type. */
    public List<OciManifestRecord> referrers(UUID repository, Digest digest, @Nullable String artifactType) {
        return manifests.findBySubject(organization(), repository, digest.toString()).stream()
                .filter(m -> artifactType == null || artifactType.equals(m.getArtifactType()))
                .flatMap(m -> packages
                        .versionById(m.getId().getVersionId())
                        .flatMap(version -> record(m, Digest.parse(version.getVersion())))
                        .stream())
                .sorted(Comparator.comparing(record -> record.getDigest().toString()))
                .toList();
    }

    private void requireReferenced(UUID repository, OciManifest manifest) {
        for (OciDescriptor blob : manifest.getBlobs()) {
            if (!blobLinked(repository, blob.getDigest())) {
                throw new OciException(OciErrorCode.MANIFEST_BLOB_UNKNOWN, "Blob unknown: " + blob.getDigest());
            }
        }
        for (OciDescriptor child : manifest.getManifests()) {
            if (find(repository, child.getDigest()).isEmpty()) {
                throw new OciException(OciErrorCode.MANIFEST_BLOB_UNKNOWN, "Manifest unknown: " + child.getDigest());
            }
        }
    }

    /** Whether the repository may read the blob: pushed or mounted there. */
    private boolean blobLinked(UUID repository, Digest digest) {
        return links.linked(organization(), repository, digest.toString());
    }

    /** The manifest's artifact type, or for an image without one, its config's media type. */
    static @Nullable String artifactType(OciManifest manifest) {
        if (manifest.getArtifactType() != null || manifest.isIndex()) {
            return manifest.getArtifactType();
        }
        return manifest.getBlobs().getFirst().getMediaType();
    }

    private Optional<OciManifestRecord> record(PackageVersion version, Digest digest) {
        return manifests.findOne(organization(), version.getId()).flatMap(m -> record(m, digest));
    }

    private Optional<OciManifestRecord> record(OciManifestEntity manifest, Digest digest) {
        return packages.fileById(manifest.getFileId())
                .map(file -> new OciManifestRecord(
                        digest, manifest.getMediaType(), manifest.getArtifactType(), file.getSize()));
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
