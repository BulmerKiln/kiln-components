/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.store;

import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/** Allowed domains: an instance table, read at sign-in before any organization is known. */
public final class AllowedDomainStore {

    private static final String COLUMNS = "connector_id, domain, organization_id, source";

    private AllowedDomainStore() {}

    /** The allowed domain for exactly this connector and domain; a parent domain never matches. */
    public static Optional<AllowedDomainEntity> find(DSLContext tx, String connectorId, String domain) {
        return tx.fetchOptional(
                        "SELECT " + COLUMNS + " FROM allowed_domains WHERE connector_id = ? AND domain = ?",
                        connectorId,
                        domain)
                .map(AllowedDomainStore::allowedDomain);
    }

    public static List<AllowedDomainEntity> bySource(DSLContext tx, AllowedDomainSource source) {
        return tx.fetch("SELECT " + COLUMNS + " FROM allowed_domains WHERE source = ?", source.stored())
                .map(AllowedDomainStore::allowedDomain);
    }

    /** Adds or repoints an allowed domain, taking it over as {@code source}. */
    public static void put(DSLContext tx, AllowedDomainEntity domain) {
        tx.execute(
                "INSERT INTO allowed_domains (connector_id, domain, organization_id, source) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (connector_id, domain)"
                        + " DO UPDATE SET organization_id = excluded.organization_id, source = excluded.source",
                domain.getConnectorId(),
                domain.getDomain(),
                domain.getOrganizationId(),
                domain.getSource().stored());
    }

    public static void delete(DSLContext tx, String connectorId, String domain) {
        tx.execute("DELETE FROM allowed_domains WHERE connector_id = ? AND domain = ?", connectorId, domain);
    }

    static AllowedDomainEntity allowedDomain(Record row) {
        return new AllowedDomainEntity(
                row.get("connector_id", String.class),
                row.get("domain", String.class),
                row.get("organization_id", UUID.class),
                AllowedDomainSource.from(row.get("source", String.class)));
    }
}
