/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import com.bulmerkiln.auth.user.store.AllowedDomainStore;
import com.bulmerkiln.database.Database;
import java.util.List;

/** Allowed domains: which verified emails, through which connector, create users in which organization (AUTH-23). */
public final class AllowedDomainService {

    private final Database database;

    public AllowedDomainService(Database database) {
        this.database = database;
    }

    /**
     * Makes the config rows exactly {@code configured}: adds and repoints those listed, removes those
     * no longer listed. Runs on every start.
     */
    public void reconcile(List<AllowedDomainEntity> configured) {
        database.instanceTransaction(tx -> {
            for (AllowedDomainEntity existing : AllowedDomainStore.bySource(tx, AllowedDomainSource.CONFIG)) {
                boolean kept = configured.stream()
                        .anyMatch(domain -> domain.getConnectorId().equals(existing.getConnectorId())
                                && domain.getDomain().equals(existing.getDomain()));
                if (!kept) {
                    AllowedDomainStore.delete(tx, existing.getConnectorId(), existing.getDomain());
                }
            }
            for (AllowedDomainEntity domain : configured) {
                AllowedDomainStore.put(tx, domain);
            }
            return Boolean.TRUE;
        });
    }
}
