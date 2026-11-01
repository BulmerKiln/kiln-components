/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.lookup;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.entity.RoleBindingEntity;
import com.bulmerkiln.permissions.entity.RoleBindingKey;
import com.bulmerkiln.permissions.mapper.RoleBindingMapper;
import com.bulmerkiln.permissions.repository.RoleBindingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Role bindings kept in the database, for the current organization (PERM-13). Only the bindings
 * whose subject is the principal, or one of a member's teams, are read.
 */
public final class StoredRoleBindingLookup implements Lookup<Principal, List<RoleBinding>> {

    private final Database database;
    private final RoleBindingRepository bindings;
    private final RoleBindingMapper mapper;

    public StoredRoleBindingLookup(Database database, RoleBindingRepository bindings, RoleBindingMapper mapper) {
        this.database = database;
        this.bindings = bindings;
        this.mapper = mapper;
    }

    @Override
    public List<RoleBinding> of(Principal principal) {
        List<UUID> subjects = new ArrayList<>();
        switch (principal) {
            case Principal.Member member -> {
                subjects.add(member.getId().getValue());
                member.getTeams().forEach(team -> subjects.add(team.getValue()));
            }
            case Principal.ServiceAccount account ->
                subjects.add(account.getId().getValue());
            case Principal.Workload _, Principal.Anonymous _ -> {
                return List.of();
            }
        }
        return database.transaction(() -> bindings.findBySubjects(organization(), subjects).stream()
                .map(mapper::toRoleBinding)
                .toList());
    }

    /** Grants a role, for the current organization. Granting one that's already held changes nothing. */
    public void add(RoleBinding binding) {
        RoleBindingEntity row = mapper.toEntity(new RoleBindingKey(organization(), UUID.randomUUID()), binding);
        database.run(() -> bindings.addIfAbsent(
                row.getId().getOrganizationId(),
                row.getId().getId(),
                row.getSubjectKind(),
                row.getSubjectId(),
                row.getRole(),
                row.getNamespace(),
                row.getRepository()));
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
