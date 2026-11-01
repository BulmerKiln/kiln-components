/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.mapper;

import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Ids.TeamId;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.entity.RoleBindingEntity;
import com.bulmerkiln.permissions.entity.RoleBindingKey;
import java.util.Locale;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Role bindings as rows of {@code role_bindings} and back. A subject is written as its kind
 * ({@code team}, {@code member} or {@code service_account}) and ID, a role in lower case.
 */
@Mapper
public interface RoleBindingMapper {

    @Mapping(target = "subject", source = ".")
    @Mapping(target = "scope", source = ".")
    RoleBinding toRoleBinding(RoleBindingEntity entity);

    @Mapping(target = "id", source = "key")
    @Mapping(target = "subjectKind", source = "binding.subject")
    @Mapping(target = "subjectId", source = "binding.subject")
    @Mapping(target = "role", source = "binding.role")
    @Mapping(target = "namespace", source = "binding.scope.namespace")
    @Mapping(target = "repository", source = "binding.scope.repository")
    RoleBindingEntity toEntity(RoleBindingKey key, RoleBinding binding);

    default RoleBinding.Subject toSubject(RoleBindingEntity entity) {
        UUID id = entity.getSubjectId();
        return switch (entity.getSubjectKind()) {
            case "team" -> new RoleBinding.Subject.Team(new TeamId(id));
            case "member" -> new RoleBinding.Subject.Member(new MemberId(id));
            default -> new RoleBinding.Subject.ServiceAccount(new ServiceAccountId(id));
        };
    }

    default Scope toScope(RoleBindingEntity entity) {
        return new Scope(entity.getNamespace(), entity.getRepository());
    }

    default String toSubjectKind(RoleBinding.Subject subject) {
        return switch (subject) {
            case RoleBinding.Subject.Team _ -> "team";
            case RoleBinding.Subject.Member _ -> "member";
            case RoleBinding.Subject.ServiceAccount _ -> "service_account";
        };
    }

    default UUID toSubjectId(RoleBinding.Subject subject) {
        return switch (subject) {
            case RoleBinding.Subject.Team team -> team.getId().getValue();
            case RoleBinding.Subject.Member member -> member.getId().getValue();
            case RoleBinding.Subject.ServiceAccount account -> account.getId().getValue();
        };
    }

    default String toColumn(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    default Role toRole(String value) {
        return Role.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
