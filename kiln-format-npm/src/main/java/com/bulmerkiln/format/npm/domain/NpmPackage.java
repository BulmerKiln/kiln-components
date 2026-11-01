/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.registry.domain.RegistryPackage;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Value;

/**
 * A package, every version of it with its document, and its dist-tags by version, read in one
 * transaction.
 */
@Value
public final class NpmPackage {

    private final RegistryPackage stored;

    private final List<NpmPublishedVersion> versions;

    private final Map<String, String> distTags;

    public NpmPublishedVersion version(String version) {
        return versions.stream()
                .filter(v -> v.getVersion().equals(version))
                .findFirst()
                .orElseThrow(NpmException::notFound);
    }

    /** The dist-tags that point at a version the caller can read. */
    public Map<String, String> readableTags(List<NpmPublishedVersion> readable) {
        Map<String, String> tags = new TreeMap<>();
        distTags.forEach((tag, version) -> {
            if (readable.stream().anyMatch(v -> v.getVersion().equals(version))) {
                tags.put(tag, version);
            }
        });
        return tags;
    }
}
