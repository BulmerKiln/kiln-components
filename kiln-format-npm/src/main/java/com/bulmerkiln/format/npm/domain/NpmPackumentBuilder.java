/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import java.time.Instant;
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
    static String revision(Map<String, String> distTags, List<NpmPublishedVersion> versions) {
        List<String> names =
                versions.stream().map(NpmPublishedVersion::getVersion).toList();
        return versions.size() + "-" + Integer.toHexString(Objects.hash(distTags, names));
    }

    /** The full packument; {@code modified} is the package's last change, whatever it was. */
    public static String full(
            NpmPackageRequest request,
            Map<String, String> distTags,
            List<NpmPublishedVersion> versions,
            Instant modified) {
        ObjectNode packument = JSON.createObjectNode();
        packument.put("_id", request.getName().toString());
        packument.put("_rev", revision(distTags, versions));
        packument.put("name", request.getName().toString());
        packument.set("dist-tags", JSON.valueToTree(distTags));
        ObjectNode all = packument.putObject("versions");
        ObjectNode time = packument.putObject("time");
        if (!versions.isEmpty()) {
            time.put("created", versions.getFirst().getPublishedAt().toString());
            time.put("modified", modified.toString());
        }
        for (NpmPublishedVersion version : versions) {
            all.set(version.getVersion(), manifest(request, version));
            time.put(version.getVersion(), version.getPublishedAt().toString());
        }
        return JSON.writeValueAsString(packument);
    }

    public static String abbreviated(
            NpmPackageRequest request,
            Map<String, String> distTags,
            List<NpmPublishedVersion> versions,
            Instant modified) {
        ObjectNode packument = JSON.createObjectNode();
        packument.put("name", request.getName().toString());
        if (!versions.isEmpty()) {
            packument.put("modified", modified.toString());
        }
        packument.set("dist-tags", JSON.valueToTree(distTags));
        ObjectNode all = packument.putObject("versions");
        for (NpmPublishedVersion version : versions) {
            ObjectNode manifest = manifest(request, version);
            manifest.retain(ABBREVIATED);
            all.set(version.getVersion(), manifest);
        }
        return JSON.writeValueAsString(packument);
    }

    /** A version as published, with its tarball link pointing at this registry. */
    public static ObjectNode manifest(NpmPackageRequest request, NpmPublishedVersion version) {
        JsonNode stored = JSON.readTree(version.getDocument());
        ObjectNode manifest = stored instanceof ObjectNode object ? object : JSON.createObjectNode();
        JsonNode dist = manifest.get("dist");
        ObjectNode rewritten = dist instanceof ObjectNode object ? object : manifest.putObject("dist");
        rewritten.put("tarball", request.tarballUrl(version.getVersion()));
        manifest.set("dist", rewritten);
        return manifest;
    }
}
