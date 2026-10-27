/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.store;

import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.domain.NpmPackageName;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.entity.NpmPackageEntity;
import com.bulmerkiln.registry.store.PackageStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jooq.DSLContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * An npm package as stored in the registry: its versions, and its dist-tags, which live in the
 * package's metadata as {@code {"dist-tags": {...}}}.
 */
public final class NpmPackageStore {

    private NpmPackageStore() {}

    public static Optional<NpmPackageEntity> load(DSLContext tx, NpmPackageName name) {
        return PackageStore.find(tx, NpmFormatModule.FORMAT, name.toString())
                .map(entry ->
                        new NpmPackageEntity(entry, PackageStore.versions(tx, entry.id()), distTags(entry.metadata())));
    }

    public static Map<String, String> distTags(String metadata) {
        Map<String, String> tags = new LinkedHashMap<>();
        JsonNode stored = NpmPackumentBuilder.JSON.readTree(metadata).get("dist-tags");
        if (stored instanceof ObjectNode object) {
            object.properties()
                    .forEach(field -> tags.put(field.getKey(), field.getValue().asString()));
        }
        return tags;
    }

    public static String metadata(Map<String, String> distTags) {
        ObjectNode metadata = NpmPackumentBuilder.JSON.createObjectNode();
        metadata.set("dist-tags", NpmPackumentBuilder.JSON.valueToTree(new TreeMap<>(distTags)));
        return NpmPackumentBuilder.JSON.writeValueAsString(metadata);
    }
}
