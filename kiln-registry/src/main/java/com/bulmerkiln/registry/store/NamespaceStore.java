/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.store;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.registry.domain.Namespace;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * Finds the namespace that owns an artifact name, inside the caller's transaction. Each lookup
 * takes the longest matching prefix, and a name no namespace owns belongs to none (REG-1).
 */
public final class NamespaceStore {

    private static final String COLUMNS =
            "id, name, visibility, oci_prefix, npm_scope, npm_prefixes, maven_group_prefixes, cargo_prefixes";

    private NamespaceStore() {}

    public static void create(DSLContext tx, Namespace namespace) {
        tx.execute(
                "INSERT INTO namespaces (organization_id, id, name, visibility, oci_prefix, npm_scope, npm_prefixes,"
                        + " maven_group_prefixes, cargo_prefixes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                organization(),
                namespace.getId(),
                namespace.getName(),
                namespace.getVisibility().name().toLowerCase(Locale.ROOT),
                namespace.getOciPrefix(),
                namespace.getNpmScope(),
                namespace.getNpmPrefixes().toArray(String[]::new),
                namespace.getMavenGroupPrefixes().toArray(String[]::new),
                namespace.getCargoPrefixes().toArray(String[]::new));
    }

    public static Optional<Namespace> byName(DSLContext tx, String name) {
        return one(tx, "name = ?", name);
    }

    public static Optional<Namespace> byId(DSLContext tx, UUID id) {
        return one(tx, "id = ?", id);
    }

    /** The namespace owning OCI repository {@code repository}: {@code acme} owns {@code acme/api}. */
    public static Optional<Namespace> forOci(DSLContext tx, String repository) {
        return one(
                tx,
                "(oci_prefix = ? OR starts_with(?, oci_prefix || '/')) ORDER BY length(oci_prefix) DESC LIMIT 1",
                repository,
                repository);
    }

    /** The namespace owning npm scope {@code scope}, such as {@code @acme}. */
    public static Optional<Namespace> forNpm(DSLContext tx, String scope) {
        return one(tx, "npm_scope = ?", scope);
    }

    /** The namespace owning unscoped npm package {@code name}: {@code acme-} owns {@code acme-ui}. */
    public static Optional<Namespace> forNpmName(DSLContext tx, String name) {
        return one(
                tx,
                "EXISTS (SELECT 1 FROM unnest(npm_prefixes) p WHERE starts_with(?, p))"
                        + " ORDER BY (SELECT max(length(p)) FROM unnest(npm_prefixes) p"
                        + " WHERE starts_with(?, p)) DESC LIMIT 1",
                name,
                name);
    }

    /** The namespace owning Maven group {@code groupId}: {@code org.acme} owns {@code org.acme.core}. */
    public static Optional<Namespace> forMaven(DSLContext tx, String groupId) {
        return one(
                tx,
                "EXISTS (SELECT 1 FROM unnest(maven_group_prefixes) p WHERE ? = p OR starts_with(?, p || '.'))"
                        + " ORDER BY (SELECT max(length(p)) FROM unnest(maven_group_prefixes) p"
                        + " WHERE ? = p OR starts_with(?, p || '.')) DESC LIMIT 1",
                groupId,
                groupId,
                groupId,
                groupId);
    }

    /** The namespace owning a crate, by its normalized name: {@code acme-} owns {@code acme-core}. */
    public static Optional<Namespace> forCargo(DSLContext tx, String normalizedName) {
        return one(
                tx,
                "EXISTS (SELECT 1 FROM unnest(cargo_prefixes) p WHERE starts_with(?, p))"
                        + " ORDER BY (SELECT max(length(p)) FROM unnest(cargo_prefixes) p"
                        + " WHERE starts_with(?, p)) DESC LIMIT 1",
                normalizedName,
                normalizedName);
    }

    private static Optional<Namespace> one(DSLContext tx, String condition, Object... values) {
        Object[] binds = new Object[values.length + 1];
        binds[0] = organization();
        System.arraycopy(values, 0, binds, 1, values.length);
        return tx.fetchOptional(
                        "SELECT " + COLUMNS + " FROM namespaces WHERE organization_id = ? AND " + condition, binds)
                .map(NamespaceStore::namespace);
    }

    private static Namespace namespace(Record row) {
        return new Namespace(
                row.get("id", UUID.class),
                row.get("name", String.class),
                Visibility.valueOf(row.get("visibility", String.class).toUpperCase(Locale.ROOT)),
                row.get("oci_prefix", String.class),
                row.get("npm_scope", String.class),
                strings(row.get("npm_prefixes", String[].class)),
                strings(row.get("maven_group_prefixes", String[].class)),
                strings(row.get("cargo_prefixes", String[].class)));
    }

    private static List<String> strings(String[] values) {
        return Arrays.asList(values);
    }

    static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
