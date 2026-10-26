/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A strictly parsed Maven repository path (Maven "Repository Layout"):
 * {@code group/as/path/artifactId/version/artifactId-version[-classifier].ext}, the
 * {@code maven-metadata.xml} files, and their checksum sidecars.
 */
public sealed interface MavenPath {

    int MAX_LENGTH = 1024;

    String groupId();

    String artifactId();

    /** Set when the path is a checksum sidecar, such as {@code lib-1.0.jar.sha1}. */
    @Nullable
    ChecksumAlgorithm checksum();

    /**
     * A file of a version: a jar, POM, {@code .module}, signature or other attachment.
     *
     * @param buildVersion the timestamped build of a SNAPSHOT, such as {@code 1.0-20261025.101500-3}
     */
    record ArtifactFile(
            String groupId,
            String artifactId,
            String version,
            @Nullable String buildVersion,
            @Nullable String classifier,
            String extension,
            @Nullable ChecksumAlgorithm checksum)
            implements MavenPath {

        public boolean isSnapshot() {
            return version.endsWith(SNAPSHOT);
        }
    }

    /** A {@code maven-metadata.xml}: per artifact, or per SNAPSHOT version when {@code version} is set. */
    record Metadata(
            String groupId,
            String artifactId,
            @Nullable String version,
            @Nullable ChecksumAlgorithm checksum) implements MavenPath {}

    String SNAPSHOT = "-SNAPSHOT";
    String METADATA = "maven-metadata.xml";

    /** Segments can't be {@code .} or {@code ..}, or start with a dot, so no path can traverse. */
    Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");

    Pattern CLASSIFIER = Pattern.compile("[A-Za-z0-9_-]+");
    Pattern EXTENSION_PART = Pattern.compile("[A-Za-z0-9]+");
    Pattern DOT = Pattern.compile("\\.");
    Pattern TIMESTAMP = Pattern.compile("\\d{8}\\.\\d{6}-\\d{1,9}");

    /** Parses a path relative to the repository root, without a leading slash. */
    static MavenPath parse(String path) {
        List<String> segments = segments(path);
        String fileName = segments.getLast();
        ChecksumAlgorithm checksum = ChecksumAlgorithm.ofFileName(fileName).orElse(null);
        String base = checksum == null
                ? fileName
                : fileName.substring(0, fileName.length() - checksum.suffix().length());
        return base.equals(METADATA) ? metadata(segments, checksum) : artifactFile(segments, base, checksum);
    }

    private static List<String> segments(String path) {
        if (path.length() > MAX_LENGTH) {
            throw new InvalidMavenPathException("Path too long");
        }
        List<String> segments = Arrays.asList(path.split("/", -1));
        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches()) {
                throw new InvalidMavenPathException("Invalid path segment");
            }
        }
        return segments;
    }

    /** Per-version metadata exists only for a {@code -SNAPSHOT} version; otherwise it's per artifact. */
    private static Metadata metadata(List<String> segments, @Nullable ChecksumAlgorithm checksum) {
        int n = segments.size();
        if (n >= 4 && segments.get(n - 2).endsWith(SNAPSHOT)) {
            return new Metadata(groupId(segments, n - 3), segments.get(n - 3), segments.get(n - 2), checksum);
        }
        if (n >= 3) {
            return new Metadata(groupId(segments, n - 2), segments.get(n - 2), null, checksum);
        }
        throw new InvalidMavenPathException("Metadata needs a groupId and artifactId");
    }

    /** {@code group/as/path/artifactId/version/artifactId-version[-classifier].ext}. */
    private static ArtifactFile artifactFile(List<String> segments, String base, @Nullable ChecksumAlgorithm checksum) {
        int n = segments.size();
        if (n < 4) {
            throw new InvalidMavenPathException("A file path needs a groupId, artifactId and version");
        }
        String artifactId = segments.get(n - 3);
        String version = segments.get(n - 2);
        String prefix = artifactId + "-";
        if (!base.startsWith(prefix)) {
            throw new InvalidMavenPathException("The file name does not start with the artifactId");
        }
        String afterArtifact = base.substring(prefix.length());
        String buildVersion = buildVersion(afterArtifact, version);
        String afterVersion = afterArtifact.substring(buildVersion == null ? version.length() : buildVersion.length());
        String classifier = classifier(afterVersion);
        String extension =
                extension(classifier == null ? afterVersion : afterVersion.substring(classifier.length() + 1));
        return new ArtifactFile(
                groupId(segments, n - 3), artifactId, version, buildVersion, classifier, extension, checksum);
    }

    /**
     * The timestamped build a {@code -SNAPSHOT} file names, such as {@code 1.0-20261025.101500-3},
     * or null when the file names its directory's version exactly.
     */
    private static @Nullable String buildVersion(String afterArtifact, String version) {
        if (afterArtifact.startsWith(version)) {
            return null;
        }
        if (!version.endsWith(SNAPSHOT)) {
            throw new InvalidMavenPathException("The file name does not match the version");
        }
        String baseVersion = version.substring(0, version.length() - SNAPSHOT.length());
        Matcher timestamp = TIMESTAMP.matcher(afterArtifact);
        boolean timestamped = afterArtifact.startsWith(baseVersion + "-")
                && timestamp
                        .region(baseVersion.length() + 1, afterArtifact.length())
                        .lookingAt();
        if (!timestamped) {
            throw new InvalidMavenPathException("The file name does not match the version");
        }
        return afterArtifact.substring(0, timestamp.end());
    }

    /** The classifier after the version, as in {@code -sources.jar}, or null if there's none. */
    private static @Nullable String classifier(String afterVersion) {
        if (!afterVersion.startsWith("-")) {
            return null;
        }
        int dot = afterVersion.indexOf('.');
        if (dot < 0) {
            throw new InvalidMavenPathException("The file has no extension");
        }
        String classifier = afterVersion.substring(1, dot);
        if (!CLASSIFIER.matcher(classifier).matches()) {
            throw new InvalidMavenPathException("Invalid classifier");
        }
        return classifier;
    }

    /** One or more dot-separated alphanumeric parts, such as {@code jar}, {@code tar.gz} or {@code jar.asc}. */
    private static String extension(String dotExtension) {
        if (!dotExtension.startsWith(".")) {
            throw new InvalidMavenPathException("Invalid extension");
        }
        String extension = dotExtension.substring(1);
        for (String part : DOT.split(extension, -1)) {
            if (!EXTENSION_PART.matcher(part).matches()) {
                throw new InvalidMavenPathException("Invalid extension");
            }
        }
        return extension;
    }

    private static String groupId(List<String> segments, int end) {
        return String.join(".", segments.subList(0, end));
    }
}
