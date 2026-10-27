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
import org.jooq.DSLContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** One version being published: checked, then stored with its tarball and dist-tags. */
record NpmPublication(String version, ObjectNode manifest, String file, ObjectNode distTags) {

    /** The single version and tarball a publish must carry, matching the URL's package. */
    static NpmPublication of(NpmPackageRequest request, NpmPublishRequest body) {
        if (body.versions().size() != 1 || body.attachments().size() != 1) {
            throw invalid("Publish exactly one version with its tarball");
        }
        var entry = body.versions().properties().iterator().next();
        NpmVersion version = new NpmVersion(entry.getKey());
        ObjectNode manifest = NpmPublishService.object(entry.getValue());
        if (!request.name().toString().equals(NpmPublishService.text(manifest, "name"))
                || !version.value().equals(NpmPublishService.text(manifest, "version"))) {
            throw invalid("The version's name and version must match the package");
        }
        String file = body.attachments().getFirst();
        if (!request.tarballFile(version.value()).equals(file)) {
            throw invalid("The tarball must be named " + request.tarballFile(version.value()));
        }
        return new NpmPublication(version.value(), manifest, file, body.distTags());
    }

    /** Checks the tarball against {@code dist.integrity} and {@code dist.shasum} (FMT-NPM-4, FMT-NPM-5). */
    static void verify(NpmPublication publication, NpmTarballSink tarball) throws IOException {
        JsonNode dist = publication.manifest().get("dist");
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
        String name = request.name().toString();
        PackageEntity entry = PackageStore.create(tx, NpmFormatModule.FORMAT, namespace.id(), name, name);
        PackageVersionEntity stored =
                PackageStore.addVersion(tx, entry.id(), version, NpmPackumentBuilder.JSON.writeValueAsString(manifest));
        BlobRecordStore.record(tx, blob.digest(), blob.size());
        PackageStore.addFile(tx, stored.id(), file, blob.digest(), blob.size());
        Map<String, String> tags = NpmPackageStore.distTags(entry.metadata());
        distTags.properties().forEach(tag -> {
            if (version.equals(tag.getValue().asString())) {
                tags.put(NpmRoute.tag(tag.getKey()), version);
            }
        });
        tags.putIfAbsent("latest", version);
        PackageStore.updateMetadata(tx, entry.id(), NpmPackageStore.metadata(tags));
    }

    /** {@code npm deprecate}: applies each version's {@code deprecated} message, and nothing else. */
    static void deprecate(Database database, NpmPackageRequest request, ObjectNode versions) {
        database.run(tx -> {
            NpmPackageEntity snapshot = NpmPackageStore.load(tx, request.name()).orElseThrow(NpmException::notFound);
            versions.properties().forEach(sent -> {
                PackageVersionEntity stored = snapshot.version(sent.getKey());
                ObjectNode manifest = NpmPublishService.object(NpmPackumentBuilder.JSON.readTree(stored.metadata()));
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
                        tx, stored.id(), NpmPackumentBuilder.JSON.writeValueAsString(manifest));
            });
        });
    }

    private static NpmException invalid(String message) {
        return new NpmException(Reason.INVALID, message);
    }
}
