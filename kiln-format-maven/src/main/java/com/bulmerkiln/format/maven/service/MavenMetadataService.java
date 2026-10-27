/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.domain.MavenMetadata;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.format.maven.domain.MavenSnapshots;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.store.PackageStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads {@code maven-metadata.xml} from the database (FMT-MVN-15). A version is listed once its
 * POM is stored and only while no block refuses it, so blocked versions stop resolving at once.
 */
public final class MavenMetadataService {

    private final Database database;
    private final MavenAccessService access;

    public MavenMetadataService(Database database, MavenAccessService access) {
        this.database = database;
        this.access = access;
    }

    public byte[] read(MavenPath.Metadata metadata) {
        Optional<Namespace> namespace = access.require(metadata, Operation.PULL);
        access.checkRead(metadata, namespace, metadata.version());
        String xml = metadata.version() == null ? artifact(metadata, namespace) : snapshot(metadata);
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private String artifact(MavenPath.Metadata metadata, Optional<Namespace> namespace) {
        List<PackageVersionEntity> visible = database.transaction(tx -> {
            PackageEntity entry = PackageStore.find(tx, MavenFormatModule.FORMAT, MavenAccessService.name(metadata))
                    .orElseThrow(MavenMetadataService::notFound);
            List<PackageVersionEntity> withPom = new ArrayList<>();
            for (PackageVersionEntity version : PackageStore.versions(tx, entry.id())) {
                String pom = metadata.artifactId() + "-" + version.version() + ".pom";
                boolean hasPom = PackageStore.files(tx, version.id()).stream()
                        .anyMatch(f -> f.name().equals(pom) || isSnapshotPom(version.version(), f.name()));
                if (hasPom) {
                    withPom.add(version);
                }
            }
            return withPom;
        });
        List<PackageVersionEntity> readable = visible.stream()
                .filter(v ->
                        access.readRefusal(metadata, namespace, v.version()).isEmpty())
                .toList();
        if (readable.isEmpty()) {
            throw notFound();
        }
        return MavenMetadata.artifact(
                metadata.groupId(),
                metadata.artifactId(),
                readable.stream().map(PackageVersionEntity::version).toList(),
                readable.getLast().publishedAt());
    }

    /** The latest build of each classifier and extension of a snapshot version. */
    private String snapshot(MavenPath.Metadata metadata) {
        String version = Objects.requireNonNull(metadata.version());
        List<MavenSnapshots.Build> builds = database.transaction(tx -> PackageStore.find(
                        tx, MavenFormatModule.FORMAT, MavenAccessService.name(metadata))
                .flatMap(entry -> PackageStore.version(tx, entry.id(), version))
                .map(v -> MavenSnapshots.builds(
                        new MavenPath.ArtifactFile(
                                metadata.groupId(), metadata.artifactId(), version, null, null, "pom", null),
                        PackageStore.files(tx, v.id())))
                .orElseThrow(MavenMetadataService::notFound));
        if (builds.isEmpty()) {
            throw notFound();
        }
        Comparator<MavenSnapshots.Build> newest = Comparator.comparing(MavenSnapshots.Build::timestamp)
                .thenComparingInt(MavenSnapshots.Build::buildNumber);
        Map<String, MavenSnapshots.Build> latest = builds.stream()
                .collect(Collectors.toMap(
                        b -> b.classifier() + "." + b.extension(),
                        b -> b,
                        (a, b) -> newest.compare(a, b) >= 0 ? a : b,
                        LinkedHashMap::new));
        Collection<MavenSnapshots.Build> values = latest.values();
        return MavenMetadata.snapshot(
                metadata.groupId(), metadata.artifactId(), version, List.copyOf(values), Instant.now());
    }

    private static boolean isSnapshotPom(String version, String fileName) {
        return version.endsWith(MavenPath.SNAPSHOT) && fileName.endsWith(".pom");
    }

    private static RefusedException notFound() {
        return new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found"));
    }
}
