/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.entity;

import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Value;

/** A package and every version of it, read in one transaction. */
@Value
public final class NpmPackageEntity {

    private final PackageEntity entry;

    private final List<PackageVersionEntity> versions;

    private final Map<String, String> distTags;

    public PackageVersionEntity version(String version) {
        return versions.stream()
                .filter(v -> v.getVersion().equals(version))
                .findFirst()
                .orElseThrow(NpmException::notFound);
    }

    /** The dist-tags that point at a version the caller can read. */
    public Map<String, String> readableTags(List<PackageVersionEntity> readable) {
        Map<String, String> tags = new TreeMap<>();
        distTags.forEach((tag, version) -> {
            if (readable.stream().anyMatch(v -> v.getVersion().equals(version))) {
                tags.put(tag, version);
            }
        });
        return tags;
    }
}
