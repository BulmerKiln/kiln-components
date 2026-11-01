/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.domain;

import com.bulmerkiln.core.annotation.UntrustedInput;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.constant.Reason;
import lombok.Value;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A version's line in its crate's index file, built from what {@code cargo publish} sends
 * (FMT-CARGO-9). Dependencies are rewritten as the index has them: {@code version_req} becomes
 * {@code req}, and a renamed dependency keeps its real name in {@code package}.
 */
@Value
public final class CargoIndexEntry {

    private final String name;

    private final String version;

    private final ObjectNode json;

    public static final JsonMapper JSON = JsonMapper.builder().build();

    @UntrustedInput
    public static CargoIndexEntry fromPublish(byte[] metadata, String checksum) {
        JsonNode sent;
        try {
            sent = JSON.readTree(metadata);
        } catch (JacksonException e) {
            throw new CargoException(Reason.INVALID, "The publish metadata is not valid JSON");
        }
        CargoCrateName name = new CargoCrateName(text(sent, "name"));
        String version = CargoRoute.version(text(sent, "vers"));
        ObjectNode entry = JSON.createObjectNode();
        entry.put("name", name.toString());
        entry.put("vers", version);
        ArrayNode deps = entry.putArray("deps");
        JsonNode sentDeps = sent.get("deps");
        if (sentDeps != null && sentDeps.isArray()) {
            sentDeps.forEach(dep -> deps.add(dependency(dep)));
        }
        entry.put("cksum", checksum);
        JsonNode features = sent.get("features");
        entry.set("features", features != null && features.isObject() ? features : JSON.createObjectNode());
        entry.put("yanked", false);
        JsonNode links = sent.get("links");
        entry.set("links", links != null && links.isString() ? links : JSON.nullNode());
        entry.put("v", 2);
        return new CargoIndexEntry(name.toString(), version, entry);
    }

    private static ObjectNode dependency(JsonNode dep) {
        ObjectNode out = JSON.createObjectNode();
        String explicit = dep.hasNonNull("explicit_name_in_toml") ? text(dep, "explicit_name_in_toml") : null;
        out.put("name", explicit == null ? text(dep, "name") : explicit);
        out.put("req", text(dep, "version_req"));
        out.set("features", dep.has("features") ? dep.get("features") : JSON.createArrayNode());
        out.put("optional", dep.path("optional").asBoolean(false));
        out.put("default_features", dep.path("default_features").asBoolean(true));
        out.set("target", dep.hasNonNull("target") ? dep.get("target") : JSON.nullNode());
        out.put("kind", dep.hasNonNull("kind") ? text(dep, "kind") : "normal");
        out.set("registry", dep.hasNonNull("registry") ? dep.get("registry") : JSON.nullNode());
        if (explicit != null) {
            out.put("package", text(dep, "name"));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw new CargoException(Reason.INVALID, "The publish metadata needs " + field);
        }
        return value.asString();
    }
}
