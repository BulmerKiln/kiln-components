/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * {@code maven-metadata.xml}, generated from the database so concurrent deploys can't overwrite
 * each other's (FMT-MVN-15). Every value is escaped, though names and versions already pass
 * {@link MavenPath}'s grammar.
 */
public final class MavenMetadata {

    private static final DateTimeFormatter UPDATED =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

    private MavenMetadata() {}

    /** Per artifact: its visible versions, oldest first, and the latest and latest release. */
    public static String artifact(String groupId, String artifactId, List<String> versions, Instant updated) {
        StringBuilder xml = header(groupId, artifactId);
        xml.append("  <versioning>\n");
        if (!versions.isEmpty()) {
            element(xml, "    ", "latest", versions.getLast());
        }
        versions.stream()
                .filter(v -> !v.endsWith(MavenPath.SNAPSHOT))
                .reduce((first, second) -> second)
                .ifPresent(release -> element(xml, "    ", "release", release));
        xml.append("    <versions>\n");
        versions.forEach(v -> element(xml, "      ", "version", v));
        xml.append("    </versions>\n");
        element(xml, "    ", "lastUpdated", UPDATED.format(updated));
        return xml.append("  </versioning>\n</metadata>\n").toString();
    }

    /** Per snapshot version: the latest build, and the latest build of each classifier and extension. */
    public static String snapshot(
            String groupId, String artifactId, String version, List<MavenSnapshots.Build> latest, Instant updated) {
        StringBuilder xml = header(groupId, artifactId);
        element(xml, "  ", "version", version);
        xml.append("  <versioning>\n");
        latest.stream()
                .max(Comparator.comparing(MavenSnapshots.Build::timestamp)
                        .thenComparingInt(MavenSnapshots.Build::buildNumber))
                .ifPresent(build -> {
                    xml.append("    <snapshot>\n");
                    element(xml, "      ", "timestamp", build.timestamp());
                    element(xml, "      ", "buildNumber", Integer.toString(build.buildNumber()));
                    xml.append("    </snapshot>\n");
                });
        element(xml, "    ", "lastUpdated", UPDATED.format(updated));
        xml.append("    <snapshotVersions>\n");
        for (MavenSnapshots.Build build : latest) {
            xml.append("      <snapshotVersion>\n");
            optionalElement(xml, "classifier", build.classifier());
            element(xml, "        ", "extension", build.extension());
            element(xml, "        ", "value", build.value());
            element(xml, "        ", "updated", build.timestamp().replace(".", ""));
            xml.append("      </snapshotVersion>\n");
        }
        return xml.append("    </snapshotVersions>\n  </versioning>\n</metadata>\n")
                .toString();
    }

    private static StringBuilder header(String groupId, String artifactId) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<metadata>\n");
        element(xml, "  ", "groupId", groupId);
        element(xml, "  ", "artifactId", artifactId);
        return xml;
    }

    private static void optionalElement(StringBuilder xml, String name, @Nullable String value) {
        if (value != null) {
            element(xml, "        ", name, value);
        }
    }

    private static void element(StringBuilder xml, String indent, String name, String value) {
        xml.append(indent)
                .append('<')
                .append(name)
                .append('>')
                .append(escape(value))
                .append("</")
                .append(name)
                .append(">\n");
    }

    static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
