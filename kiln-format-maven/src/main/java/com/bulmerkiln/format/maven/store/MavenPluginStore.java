/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.store;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.maven.domain.MavenPlugin;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** Each plugin's goal prefix, by package, for its group's metadata (FMT-MVN-17). */
public final class MavenPluginStore {

    private MavenPluginStore() {}

    /** Records or replaces the plugin entry of an artifact already deployed in {@code groupId}. */
    public static void save(DSLContext tx, UUID packageId, String groupId, MavenPlugin plugin) {
        tx.execute(
                "INSERT INTO maven_plugins (organization_id, package_id, group_id, prefix, name)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (organization_id, package_id)"
                        + " DO UPDATE SET prefix = excluded.prefix, name = excluded.name",
                organization(),
                packageId,
                groupId,
                plugin.prefix(),
                plugin.name());
    }

    /** The plugins of {@code groupId}, by artifactId. */
    public static List<MavenPlugin> inGroup(DSLContext tx, String groupId) {
        return tx.fetch(
                        "SELECT p.name AS package, m.prefix, m.name FROM maven_plugins m"
                                + " JOIN packages p ON p.organization_id = m.organization_id AND p.id = m.package_id"
                                + " WHERE m.organization_id = ? AND m.group_id = ? ORDER BY p.name",
                        organization(),
                        groupId)
                .map(row -> new MavenPlugin(
                        artifactId(row.get("package", String.class)),
                        row.get("prefix", String.class),
                        row.get("name", String.class)));
    }

    /** The artifactId of a package named {@code groupId:artifactId}. */
    private static String artifactId(String packageName) {
        return packageName.substring(packageName.indexOf(':') + 1);
    }

    private static UUID organization() {
        return OrganizationContext.current().id().value();
    }
}
