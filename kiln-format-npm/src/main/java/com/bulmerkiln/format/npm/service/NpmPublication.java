/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.domain.NpmRoute;
import com.bulmerkiln.format.npm.domain.NpmVersion;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.dto.NpmPublishRequest;
import com.bulmerkiln.format.npm.entity.NpmPackageEntity;
import com.bulmerkiln.format.npm.store.NpmPackageStore;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.BlobRecordStore;
import com.bulmerkiln.registry.store.BlockStore;
import com.bulmerkiln.registry.store.PackageStore;
import com.bulmerkiln.storage.domain.BlobInfo;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.Value;
import org.jooq.DSLContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** One version being published: checked, then stored with its tarball and dist-tags. */
@Value
final class NpmPublication {

    private final String version;

    private final ObjectNode manifest;

    private final String file;

    private final ObjectNode distTags;

    /** The single version and tarball a publish must carry, matching the URL's package. */
    static NpmPublication of(NpmPackageRequest request, NpmPublishRequest body) {
        if (body.getVersions().size() != 1 || body.getAttachments().size() != 1) {
            throw invalid("Publish exactly one version with its tarball");
        }
        var entry = body.getVersions().properties().iterator().next();
        NpmVersion version = new NpmVersion(entry.getKey());
        ObjectNode manifest = NpmPublishService.object(entry.getValue());
        if (!request.getName().toString().equals(NpmPublishService.text(manifest, "name"))
                || !version.getValue().equals(NpmPublishService.text(manifest, "version"))) {
            throw invalid("The version's name and version must match the package");
        }
        String file = body.getAttachments().getFirst();
        if (!request.tarballFile(version.getValue()).equals(file)) {
            throw invalid("The tarball must be named " + request.tarballFile(version.getValue()));
        }
        return new NpmPublication(version.getValue(), manifest, file, body.getDistTags());
    }

    /** Checks the tarball against {@code dist.integrity} and {@code dist.shasum} (FMT-NPM-4, FMT-NPM-5). */
    static void verify(NpmPublication publication, NpmTarballSink tarball) throws IOException {
        JsonNode dist = publication.getManifest().get("dist");
        String integrity = dist == null ? "" : NpmPublishService.text(dist, "integrity");
        String shasum = dist == null ? "" : NpmPublishService.text(dist, "shasum");
        tarball.digest();
        tarball.verifier().verify(integrity, shasum.isEmpty() ? null : shasum);
    }

    /**
     * Stores the version, refusing a block, a tombstone or a version that exists. The dist-tags the
     * publish names for this version move to it, and the package's first version becomes
     * {@code latest}.
     */
    void store(DSLContext tx, NpmPackageRequest request, Namespace namespace, BlobInfo blob) {
        BlockStore.checkWrite(tx, NpmAccessService.artifact(request, Optional.of(namespace), version))
                .ifPresent(refusal -> {
                    throw new RefusedException(refusal);
                });
        String name = request.getName().toString();
        PackageEntity entry = PackageStore.create(tx, NpmFormatModule.FORMAT, namespace.getId(), name, name);
        PackageVersionEntity stored = PackageStore.addVersion(
                tx, entry.getId(), version, NpmPackumentBuilder.JSON.writeValueAsString(manifest));
        BlobRecordStore.record(tx, blob.getDigest(), blob.getSize());
        PackageStore.addFile(tx, stored.getId(), file, blob.getDigest(), blob.getSize());
        Map<String, String> tags = NpmPackageStore.distTags(entry.getMetadata());
        distTags.properties().forEach(tag -> {
            if (version.equals(tag.getValue().asString())) {
                tags.put(NpmRoute.tag(tag.getKey()), version);
            }
        });
        tags.putIfAbsent("latest", version);
        PackageStore.updateMetadata(tx, entry.getId(), NpmPackageStore.metadata(tags));
    }

    /** {@code npm deprecate}: applies each version's {@code deprecated} message, and nothing else. */
    static void deprecate(Database database, NpmPackageRequest request, ObjectNode versions) {
        database.run(tx -> {
            NpmPackageEntity snapshot =
                    NpmPackageStore.load(tx, request.getName()).orElseThrow(NpmException::notFound);
            versions.properties().forEach(sent -> {
                PackageVersionEntity stored = snapshot.version(sent.getKey());
                ObjectNode manifest = NpmPublishService.object(NpmPackumentBuilder.JSON.readTree(stored.getMetadata()));
                String message = NpmPublishService.text(NpmPublishService.object(sent.getValue()), "deprecated");
                if (message.equals(NpmPublishService.text(manifest, "deprecated"))) {
                    return;
                }
                if (message.isEmpty()) {
                    manifest.remove("deprecated");
                } else {
                    manifest.put("deprecated", message);
                }
                PackageStore.updateVersionMetadata(
                        tx, stored.getId(), NpmPackumentBuilder.JSON.writeValueAsString(manifest));
            });
        });
    }

    private static NpmException invalid(String message) {
        return new NpmException(Reason.INVALID, message);
    }
}
