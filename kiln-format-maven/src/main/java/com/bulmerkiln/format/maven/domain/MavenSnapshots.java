/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import com.bulmerkiln.registry.entity.PackageFileEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A {@code -SNAPSHOT} version's timestamped builds (FMT-MVN-14). A request for {@code
 * lib-1.0-SNAPSHOT.jar} is served the latest build of that classifier and extension.
 */
public final class MavenSnapshots {

    private MavenSnapshots() {}

    /** One stored build of a snapshot file, such as {@code lib-1.0-20261025.101500-3-sources.jar}. */
    public record Build(String value, @Nullable String classifier, String extension, String fileName) {

        /** {@code 20261025.101500}: the 15 characters before the build number's dash. */
        public String timestamp() {
            int dash = value.lastIndexOf('-');
            return value.substring(dash - 15, dash);
        }

        public int buildNumber() {
            return Integer.parseInt(value.substring(value.lastIndexOf('-') + 1));
        }
    }

    /** The stored file a request names: exactly, or for a snapshot, its latest build. */
    public static Optional<PackageFileEntity> resolve(
            MavenPath.ArtifactFile file, String fileName, List<PackageFileEntity> files) {
        Optional<PackageFileEntity> exact =
                files.stream().filter(f -> f.name().equals(fileName)).findFirst();
        if (exact.isPresent() || !file.isSnapshot() || file.buildVersion() != null) {
            return exact;
        }
        return builds(file, files).stream()
                .filter(b -> Objects.equals(b.classifier(), file.classifier())
                        && b.extension().equals(file.extension()))
                .max(Comparator.comparing(Build::timestamp).thenComparingInt(Build::buildNumber))
                .flatMap(b -> files.stream()
                        .filter(f -> f.name().equals(b.fileName()))
                        .findFirst());
    }

    /** The timestamped builds among a snapshot version's files, without checksums. */
    public static List<Build> builds(MavenPath.ArtifactFile version, List<PackageFileEntity> files) {
        String directory =
                version.groupId().replace('.', '/') + "/" + version.artifactId() + "/" + version.version() + "/";
        return files.stream()
                .map(f -> MavenPath.parse(directory + f.name()))
                .filter(MavenPath.ArtifactFile.class::isInstance)
                .map(MavenPath.ArtifactFile.class::cast)
                .filter(f -> f.buildVersion() != null && f.checksum() == null)
                .map(f ->
                        new Build(Objects.requireNonNull(f.buildVersion()), f.classifier(), f.extension(), fileName(f)))
                .toList();
    }

    private static String fileName(MavenPath.ArtifactFile file) {
        String classifier = file.classifier() == null ? "" : "-" + file.classifier();
        return file.artifactId() + "-" + file.buildVersion() + classifier + "." + file.extension();
    }
}
