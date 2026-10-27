/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds packuments from stored versions: the full document, or the abbreviated one npm installs
 * from (FMT-NPM-10). Tarball links are absolute, from the configured external URL.
 */
public final class NpmPackumentBuilder {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    /** The fields of a version that the abbreviated packument keeps, as npm's registry does. */
    private static final Set<String> ABBREVIATED = Set.of(
            "name",
            "version",
            "dependencies",
            "optionalDependencies",
            "devDependencies",
            "bundleDependencies",
            "peerDependencies",
            "peerDependenciesMeta",
            "bin",
            "directories",
            "dist",
            "engines",
            "_hasShrinkwrap",
            "hasInstallScript",
            "deprecated",
            "os",
            "cpu");

    private NpmPackumentBuilder() {}

    /**
     * A CouchDB-style revision of the package's state. npm reads it to address an unpublish
     * ({@code <name>/-rev/<rev>}); Kiln doesn't check it, since every write runs in a transaction.
     */
    static String revision(Map<String, String> distTags, List<PackageVersionEntity> versions) {
        List<String> names =
                versions.stream().map(PackageVersionEntity::version).toList();
        return versions.size() + "-" + Integer.toHexString(Objects.hash(distTags, names));
    }

    public static String full(
            NpmPackageRequest request, Map<String, String> distTags, List<PackageVersionEntity> versions) {
        ObjectNode packument = JSON.createObjectNode();
        packument.put("_id", request.name().toString());
        packument.put("_rev", revision(distTags, versions));
        packument.put("name", request.name().toString());
        packument.set("dist-tags", JSON.valueToTree(distTags));
        ObjectNode all = packument.putObject("versions");
        ObjectNode time = packument.putObject("time");
        if (!versions.isEmpty()) {
            time.put("created", versions.getFirst().publishedAt().toString());
            time.put("modified", versions.getLast().publishedAt().toString());
        }
        for (PackageVersionEntity version : versions) {
            all.set(version.version(), manifest(request, version));
            time.put(version.version(), version.publishedAt().toString());
        }
        return JSON.writeValueAsString(packument);
    }

    public static String abbreviated(
            NpmPackageRequest request, Map<String, String> distTags, List<PackageVersionEntity> versions) {
        ObjectNode packument = JSON.createObjectNode();
        packument.put("name", request.name().toString());
        if (!versions.isEmpty()) {
            packument.put("modified", versions.getLast().publishedAt().toString());
        }
        packument.set("dist-tags", JSON.valueToTree(distTags));
        ObjectNode all = packument.putObject("versions");
        for (PackageVersionEntity version : versions) {
            ObjectNode manifest = manifest(request, version);
            manifest.retain(ABBREVIATED);
            all.set(version.version(), manifest);
        }
        return JSON.writeValueAsString(packument);
    }

    /** A version as published, with its tarball link pointing at this registry. */
    public static ObjectNode manifest(NpmPackageRequest request, PackageVersionEntity version) {
        JsonNode stored = JSON.readTree(version.metadata());
        ObjectNode manifest = stored instanceof ObjectNode object ? object : JSON.createObjectNode();
        JsonNode dist = manifest.get("dist");
        ObjectNode rewritten = dist instanceof ObjectNode object ? object : manifest.putObject("dist");
        rewritten.put("tarball", request.tarballUrl(version.version()));
        manifest.set("dist", rewritten);
        return manifest;
    }
}
