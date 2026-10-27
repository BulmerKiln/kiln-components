/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.store;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.permissions.RoleBindingRepository;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.domain.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.Record;

/**
 * Role bindings kept in the database, for the current organization (PERM-13). Only the bindings
 * whose subject is the principal, or one of a member's teams, are read.
 */
public final class RoleBindingStore implements RoleBindingRepository {

    private final Database database;

    public RoleBindingStore(Database database) {
        this.database = database;
    }

    @Override
    public List<RoleBinding> bindingsFor(Principal principal) {
        List<UUID> subjects = new ArrayList<>();
        switch (principal) {
            case Principal.Member member -> {
                subjects.add(member.id().value());
                member.teams().forEach(team -> subjects.add(team.value()));
            }
            case Principal.ServiceAccount account -> subjects.add(account.id().value());
            case Principal.Workload _, Principal.Anonymous _ -> {
                return List.of();
            }
        }
        return database.transaction(tx -> tx.fetch(
                        "SELECT subject_kind, subject_id, role, namespace, repository FROM role_bindings"
                                + " WHERE organization_id = ? AND subject_id = ANY (?)",
                        organization(),
                        subjects.toArray(UUID[]::new))
                .map(RoleBindingStore::binding));
    }

    /** Grants a role, for the current organization. Granting one that's already held changes nothing. */
    public void add(RoleBinding binding) {
        database.transaction(tx -> tx.execute(
                "INSERT INTO role_bindings (organization_id, id, subject_kind, subject_id, role, namespace, repository)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                organization(),
                UUID.randomUUID(),
                RoleBindingRowMapper.kind(binding.subject()),
                RoleBindingRowMapper.id(binding.subject()),
                RoleBindingRowMapper.role(binding.role()),
                binding.scope().namespace(),
                binding.scope().repository()));
    }

    private static RoleBinding binding(Record row) {
        return new RoleBinding(
                RoleBindingRowMapper.subject(row.get("subject_kind", String.class), row.get("subject_id", UUID.class)),
                RoleBindingRowMapper.role(row.get("role", String.class)),
                new Scope(row.get("namespace", String.class), row.get("repository", String.class)));
    }

    private static UUID organization() {
        return OrganizationContext.current().id().value();
    }
}
