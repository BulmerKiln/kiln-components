/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.domain.NpmPackage;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.domain.NpmPublishedVersion;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.registry.service.BlockService;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * {@code npm unpublish}, for members allowed to delete (FMT-NPM-17). Every unpublished version is
 * tombstoned, so its number can never be published again, as on the public registry.
 */
public final class NpmUnpublishService {

    /** The largest packument {@code npm unpublish} may send back; it carries no tarballs. */
    static final int MAX_PACKUMENT = 32 * 1024 * 1024;

    private final Database database;
    private final NpmAccessService access;
    private final NpmPackageService packages;
    private final BlockService blocks;

    public NpmUnpublishService(
            Database database, NpmAccessService access, NpmPackageService packages, BlockService blocks) {
        this.database = database;
        this.access = access;
        this.packages = packages;
        this.blocks = blocks;
    }

    /**
     * {@code PUT <name>/-rev/<rev>}: the packument without the versions to unpublish. Each version
     * it leaves out goes; a dist-tag that pointed at one follows the packument, or goes with it.
     */
    public void removeVersions(NpmPackageRequest request, InputStream body) throws IOException {
        access.requireToWrite(request, Operation.DELETE);
        byte[] bytes = body.readNBytes(MAX_PACKUMENT + 1);
        if (bytes.length > MAX_PACKUMENT) {
            throw new NpmException(Reason.TOO_LARGE, "The packument is too large");
        }
        JsonNode packument = NpmPackumentBuilder.JSON.readTree(bytes);
        JsonNode kept = packument.path("versions");
        JsonNode tags = packument.path("dist-tags");
        database.run(() -> {
            NpmPackage snapshot = load(request);
            List<NpmPublishedVersion> removed = snapshot.getVersions().stream()
                    .filter(version -> !kept.has(version.getVersion()))
                    .toList();
            if (removed.size() == snapshot.getVersions().size()) {
                removeAll(request, snapshot);
                return;
            }
            Set<String> gone = new HashSet<>();
            for (NpmPublishedVersion version : removed) {
                remove(request, version);
                gone.add(version.getVersion());
            }
            Map<String, String> distTags = retag(snapshot.getDistTags(), gone, kept, tags);
            packages.setDistTags(snapshot.getStored().getId(), distTags);
        });
    }

    /**
     * {@code DELETE <name>/-/<file>/-rev/<rev>}: the tarball of a version already unpublished,
     * which went with its version. A tarball whose version is still published stays.
     */
    public void removeTarball(NpmPackageRequest request, String file) {
        access.requireToWrite(request, Operation.DELETE);
        database.run(() -> {
            NpmPackage snapshot = load(request);
            boolean published = snapshot.getVersions().stream()
                    .anyMatch(
                            version -> request.tarballFile(version.getVersion()).equals(file));
            if (published) {
                throw new NpmException(Reason.CONFLICT, "Unpublish the version before its tarball");
            }
        });
    }

    /** {@code DELETE <name>/-rev/<rev>}: every version, and then the package. */
    public void removePackage(NpmPackageRequest request) {
        access.requireToWrite(request, Operation.DELETE);
        database.run(() -> removeAll(request, load(request)));
    }

    /** Each tag on an unpublished version moves where the packument says, or goes. */
    private static Map<String, String> retag(
            Map<String, String> distTags, Set<String> gone, JsonNode kept, JsonNode tags) {
        for (Map.Entry<String, String> tag : Map.copyOf(distTags).entrySet()) {
            if (gone.contains(tag.getValue())) {
                String moved = tags.path(tag.getKey()).asString("");
                if (kept.has(moved) && !gone.contains(moved)) {
                    distTags.put(tag.getKey(), moved);
                } else {
                    distTags.remove(tag.getKey());
                }
            }
        }
        return distTags;
    }

    private NpmPackage load(NpmPackageRequest request) {
        return packages.load(request.getName()).orElseThrow(NpmException::notFound);
    }

    private void removeAll(NpmPackageRequest request, NpmPackage snapshot) {
        for (NpmPublishedVersion version : snapshot.getVersions()) {
            remove(request, version);
        }
        packages.removePackage(snapshot);
    }

    private void remove(NpmPackageRequest request, NpmPublishedVersion version) {
        blocks.tombstone(NpmFormatModule.FORMAT, request.getName().toString(), version.getVersion());
        packages.removeVersion(version);
    }
}
