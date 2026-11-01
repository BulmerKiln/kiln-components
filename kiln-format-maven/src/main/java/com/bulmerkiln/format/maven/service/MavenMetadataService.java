/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.domain.MavenMetadata;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.format.maven.domain.MavenPlugin;
import com.bulmerkiln.format.maven.domain.MavenPluginMetadata;
import com.bulmerkiln.format.maven.domain.MavenSnapshots;
import com.bulmerkiln.format.maven.repository.MavenPluginRepository;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.PackageService;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads {@code maven-metadata.xml} from the database (FMT-MVN-15). A version is listed once its
 * POM is stored and only while no block refuses it, so blocked versions stop resolving at once.
 */
public final class MavenMetadataService {

    private final Database database;
    private final MavenAccessService access;
    private final PackageService packages;
    private final MavenPluginRepository plugins;

    public MavenMetadataService(
            Database database, MavenAccessService access, PackageService packages, MavenPluginRepository plugins) {
        this.database = database;
        this.access = access;
        this.packages = packages;
        this.plugins = plugins;
    }

    public byte[] read(MavenPath.Metadata metadata) {
        if (metadata.getVersion() == null) {
            return artifact(metadata).getBytes(StandardCharsets.UTF_8);
        }
        Optional<Namespace> namespace = access.require(metadata, Operation.PULL);
        access.checkRead(metadata, namespace, metadata.getVersion());
        return snapshot(metadata).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Uploaded metadata: Kiln keeps only the plugin entries of group-level metadata, each for an
     * artifact already deployed that the caller may write (FMT-MVN-17). The path {@code g/a/} is
     * both artifact {@code g:a} and group {@code g.a}.
     */
    public void upload(MavenPath.Metadata metadata, byte[] xml) {
        if (metadata.getVersion() != null || metadata.getChecksum() != null) {
            return;
        }
        String group = group(metadata);
        for (MavenPlugin plugin : MavenPluginMetadata.parse(xml)) {
            MavenPath.Metadata artifact = new MavenPath.Metadata(group, plugin.getArtifactId(), null, null);
            access.requireToWrite(artifact);
            database.run(() -> packages.find(MavenFormatModule.FORMAT, MavenAccessService.name(artifact))
                    .ifPresent(entry -> plugins.save(
                            organization(),
                            entry.getId(),
                            group,
                            plugin.getArtifactId(),
                            plugin.getPrefix(),
                            plugin.getName())));
        }
    }

    /**
     * Version-less metadata: the artifact's versions where the caller may read the artifact, and
     * the group's plugins they may read. With neither, the artifact's own refusal, or not found.
     */
    private String artifact(MavenPath.Metadata metadata) {
        List<PackageVersion> readable = access.mayRead(metadata) ? readable(metadata) : List.of();
        String group = group(metadata);
        List<MavenPlugin> inGroup = database.transaction(() -> plugins.findByGroup(organization(), group)).stream()
                .map(row -> new MavenPlugin(row.getArtifactId(), row.getPrefix(), row.getName()))
                .filter(plugin -> access.mayRead(new MavenPath.Metadata(group, plugin.getArtifactId(), null, null)))
                .toList();
        if (readable.isEmpty() && inGroup.isEmpty()) {
            access.require(metadata, Operation.PULL);
            throw notFound();
        }
        return MavenMetadata.artifact(
                metadata.getGroupId(),
                metadata.getArtifactId(),
                readable.stream().map(PackageVersion::getVersion).toList(),
                readable.isEmpty() ? null : updated(metadata),
                inGroup);
    }

    private List<PackageVersion> readable(MavenPath.Metadata metadata) {
        Optional<Namespace> namespace = access.require(metadata, Operation.PULL);
        return versions(metadata).stream()
                .filter(v ->
                        access.readRefusal(metadata, namespace, v.getVersion()).isEmpty())
                .toList();
    }

    /** When the artifact last changed, which is its metadata's {@code lastUpdated}. */
    private Instant updated(MavenPath.Metadata metadata) {
        return database.transaction(() -> packages.find(MavenFormatModule.FORMAT, MavenAccessService.name(metadata)))
                .map(RegistryPackage::getUpdatedAt)
                .orElseThrow(MavenMetadataService::notFound);
    }

    /** Versions with a POM, oldest first; none when the artifact doesn't exist. */
    private List<PackageVersion> versions(MavenPath.Metadata metadata) {
        return database.transaction(() -> {
            Optional<RegistryPackage> found =
                    packages.find(MavenFormatModule.FORMAT, MavenAccessService.name(metadata));
            if (found.isEmpty()) {
                return List.<PackageVersion>of();
            }
            RegistryPackage entry = found.get();
            List<PackageVersion> withPom = new ArrayList<>();
            for (PackageVersion version : packages.versions(entry.getId())) {
                String pom = metadata.getArtifactId() + "-" + version.getVersion() + ".pom";
                boolean hasPom = packages.files(version.getId()).stream()
                        .anyMatch(f -> f.getName().equals(pom) || isSnapshotPom(version.getVersion(), f.getName()));
                if (hasPom) {
                    withPom.add(version);
                }
            }
            return withPom;
        });
    }

    /** The group whose metadata shares this path: {@code org/acme/plugins/} is group {@code org.acme.plugins}. */
    private static String group(MavenPath.Metadata metadata) {
        return metadata.getGroupId() + "." + metadata.getArtifactId();
    }

    /** The latest build of each classifier and extension of a snapshot version. */
    private String snapshot(MavenPath.Metadata metadata) {
        String version = Objects.requireNonNull(metadata.getVersion());
        List<MavenSnapshots.Build> builds = database.transaction(() -> packages.find(
                        MavenFormatModule.FORMAT, MavenAccessService.name(metadata))
                .flatMap(entry -> packages.version(entry.getId(), version))
                .map(v -> MavenSnapshots.builds(
                        new MavenPath.ArtifactFile(
                                metadata.getGroupId(), metadata.getArtifactId(), version, null, null, "pom", null),
                        packages.files(v.getId())))
                .orElseThrow(MavenMetadataService::notFound));
        if (builds.isEmpty()) {
            throw notFound();
        }
        Comparator<MavenSnapshots.Build> newest = Comparator.comparing(MavenSnapshots.Build::timestamp)
                .thenComparingInt(MavenSnapshots.Build::buildNumber);
        Map<String, MavenSnapshots.Build> latest = builds.stream()
                .collect(Collectors.toMap(
                        b -> b.getClassifier() + "." + b.getExtension(),
                        b -> b,
                        (a, b) -> newest.compare(a, b) >= 0 ? a : b,
                        LinkedHashMap::new));
        Collection<MavenSnapshots.Build> values = latest.values();
        return MavenMetadata.snapshot(
                metadata.getGroupId(), metadata.getArtifactId(), version, List.copyOf(values), Instant.now());
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }

    private static boolean isSnapshotPom(String version, String fileName) {
        return version.endsWith(MavenPath.SNAPSHOT) && fileName.endsWith(".pom");
    }

    private static RefusedException notFound() {
        return new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found"));
    }
}
