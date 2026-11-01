/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.dto.NpmPublishRequest;
import java.io.IOException;
import lombok.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** One version being published: checked, then stored with its tarball and dist-tags. */
@Value
public final class NpmPublication {

    private final String version;

    private final ObjectNode manifest;

    private final String file;

    private final ObjectNode distTags;

    /** The single version and tarball a publish must carry, matching the URL's package. */
    public static NpmPublication of(NpmPackageRequest request, NpmPublishRequest body) {
        if (body.getVersions().size() != 1 || body.getAttachments().size() != 1) {
            throw invalid("Publish exactly one version with its tarball");
        }
        var entry = body.getVersions().properties().iterator().next();
        NpmVersion version = new NpmVersion(entry.getKey());
        ObjectNode manifest = object(entry.getValue());
        if (!request.getName().toString().equals(text(manifest, "name"))
                || !version.getValue().equals(text(manifest, "version"))) {
            throw invalid("The version's name and version must match the package");
        }
        String file = body.getAttachments().getFirst();
        if (!request.tarballFile(version.getValue()).equals(file)) {
            throw invalid("The tarball must be named " + request.tarballFile(version.getValue()));
        }
        return new NpmPublication(version.getValue(), manifest, file, body.getDistTags());
    }

    /** Checks the tarball against {@code dist.integrity} and {@code dist.shasum} (FMT-NPM-4, FMT-NPM-5). */
    public static void verify(NpmPublication publication, NpmTarballSink tarball) throws IOException {
        JsonNode dist = publication.getManifest().get("dist");
        String integrity = dist == null ? "" : text(dist, "integrity");
        String shasum = dist == null ? "" : text(dist, "shasum");
        tarball.digest();
        tarball.verifier().verify(integrity, shasum.isEmpty() ? null : shasum);
    }

    /** A version's manifest field, or an empty string. */
    public static String text(JsonNode manifest, String field) {
        JsonNode value = manifest.get(field);
        return value == null || !value.isString() ? "" : value.asString();
    }

    public static ObjectNode object(JsonNode node) {
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw new NpmException(Reason.INVALID, "A version must be an object");
    }

    private static NpmException invalid(String message) {
        return new NpmException(Reason.INVALID, message);
    }
}
