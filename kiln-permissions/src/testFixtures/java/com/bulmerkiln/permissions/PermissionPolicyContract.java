/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.permissions.Ids.MemberId;
import com.bulmerkiln.permissions.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.Ids.TeamId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The authorization matrix: every role, every action, every
 * scope level and every kind of principal, with the exact decision for each. Each
 * {@link PermissionPolicy} implementation extends this.
 */
public abstract class PermissionPolicyContract {

    protected static final TeamId PLATFORM = new TeamId(UUID.randomUUID());
    protected static final Principal.Member ADA =
            new Principal.Member(new MemberId(UUID.randomUUID()), Set.of(PLATFORM));
    protected static final Principal.Member BOB = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    protected static final Principal.ServiceAccount CI =
            new Principal.ServiceAccount(new ServiceAccountId(UUID.randomUUID()));

    protected static final Resource APP = new Resource(Scope.repository("acme", "app"), Visibility.PRIVATE);
    protected static final Resource WEB = new Resource(Scope.repository("acme", "web"), Visibility.PRIVATE);
    protected static final Resource GLOBEX = new Resource(Scope.repository("globex", "app"), Visibility.PRIVATE);
    protected static final Resource PUBLIC_APP = new Resource(Scope.repository("acme", "public"), Visibility.PUBLIC);

    /** A policy whose stored bindings are exactly {@code bindings}. */
    protected abstract PermissionPolicy policy(List<RoleBinding> bindings);

    private static RoleBinding team(Role role, Scope scope) {
        return new RoleBinding(new RoleBinding.Subject.Team(PLATFORM), role, scope);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void eachRoleAllowsExactlyItsActionsAndDeniesTheRest(Role role) {
        PermissionPolicy policy = policy(List.of(team(role, Scope.namespace("acme"))));
        for (Action action : Action.values()) {
            Decision expected = role.actions().contains(action) ? Decision.ALLOW : Decision.DENY;
            assertThat(policy.decide(ADA, action, APP)).as(role + " " + action).isEqualTo(expected);
        }
    }

    @Test
    void theRoleTableMatchesTheDesign() {
        assertThat(Role.READER.actions()).containsExactly(Action.PULL);
        assertThat(Role.WRITER.actions()).containsExactlyInAnyOrder(Action.PULL, Action.PUSH);
        assertThat(Role.SECURITY.actions())
                .containsExactlyInAnyOrder(Action.PULL, Action.BLOCK, Action.UNBLOCK, Action.VIEW_SECURITY)
                .doesNotContain(Action.PUSH, Action.DELETE, Action.MANAGE);
        assertThat(Role.ADMIN.actions()).containsExactlyInAnyOrder(Action.values());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void anOrganizationRoleCoversEveryNamespace(Role role) {
        PermissionPolicy policy = policy(List.of(team(role, Scope.organization())));
        assertThat(policy.decide(ADA, Action.PULL, APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(ADA, Action.PULL, GLOBEX)).isEqualTo(Decision.ALLOW);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void aNamespaceRoleNeverReachesAnotherNamespace(Role role) {
        PermissionPolicy policy = policy(List.of(team(role, Scope.namespace("acme"))));
        for (Action action : Action.values()) {
            assertThat(policy.decide(ADA, action, GLOBEX))
                    .as(role + " " + action)
                    .isEqualTo(Decision.HIDE);
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void aRepositoryRoleCoversOnlyThatRepository(Role role) {
        PermissionPolicy policy = policy(List.of(team(role, Scope.repository("acme", "app"))));
        assertThat(policy.decide(ADA, Action.PULL, APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(ADA, Action.PULL, WEB)).isEqualTo(Decision.HIDE);
        assertThat(policy.decide(ADA, Action.PULL, new Resource(Scope.namespace("acme"), Visibility.PRIVATE)))
                .isEqualTo(Decision.HIDE);
    }

    @Test
    void namespaceNamesMatchExactly() {
        PermissionPolicy policy = policy(List.of(team(Role.ADMIN, Scope.namespace("acme"))));
        for (String lookalike : List.of("Acme", "acme-evil", "acm", "acme/", "\u0430cme")) {
            assertThat(policy.decide(
                            ADA, Action.PULL, new Resource(Scope.repository(lookalike, "app"), Visibility.PRIVATE)))
                    .as(lookalike)
                    .isEqualTo(Decision.HIDE);
        }
    }

    /** No teams and no bindings: least privilege. */
    @ParameterizedTest
    @EnumSource(Action.class)
    void aMemberWithNoRolesCanDoNothing(Action action) {
        PermissionPolicy policy = policy(List.of(team(Role.ADMIN, Scope.organization())));
        assertThat(policy.decide(BOB, action, APP)).isEqualTo(Decision.HIDE);
        assertThat(policy.allowed(BOB, APP)).isEmpty();
        // A member knows their own organization exists, so organization actions are 403, not 404.
        assertThat(policy.decide(BOB, action, Resource.organization())).isEqualTo(Decision.DENY);
    }

    @Test
    void rolesGrantedToAMemberDirectlyCount() {
        PermissionPolicy policy = policy(List.of(
                new RoleBinding(new RoleBinding.Subject.Member(BOB.id()), Role.WRITER, Scope.namespace("acme"))));
        assertThat(policy.decide(BOB, Action.PUSH, APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(ADA, Action.PUSH, APP)).isEqualTo(Decision.HIDE);
    }

    @Test
    void serviceAccountsGetOnlyTheirOwnBindings() {
        PermissionPolicy policy = policy(List.of(
                new RoleBinding(new RoleBinding.Subject.ServiceAccount(CI.id()), Role.WRITER, Scope.namespace("acme")),
                team(Role.ADMIN, Scope.organization())));
        assertThat(policy.decide(CI, Action.PUSH, APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(CI, Action.DELETE, APP)).isEqualTo(Decision.DENY);
        assertThat(policy.decide(CI, Action.PULL, GLOBEX)).isEqualTo(Decision.HIDE);
    }

    @Test
    void workloadsGetExactlyTheirTrustedPublisherGrants() {
        Principal.Workload ci = new Principal.Workload(
                "github-acme-app", Set.of(new Principal.Grant(Action.PUSH, Scope.repository("acme", "app"))));
        PermissionPolicy policy = policy(List.of(team(Role.ADMIN, Scope.organization())));

        assertThat(policy.decide(ci, Action.PUSH, APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(ci, Action.PULL, APP)).isEqualTo(Decision.DENY);
        assertThat(policy.decide(ci, Action.PUSH, WEB)).isEqualTo(Decision.HIDE);
        assertThat(policy.decide(ci, Action.MANAGE, Resource.organization())).isEqualTo(Decision.HIDE);
    }

    @Test
    void anyoneMayPullPublicContentAndNothingMore() {
        PermissionPolicy policy = policy(List.of());
        Principal anonymous = new Principal.Anonymous();
        assertThat(policy.decide(anonymous, Action.PULL, PUBLIC_APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(BOB, Action.PULL, PUBLIC_APP)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(BOB, Action.PUSH, PUBLIC_APP)).isEqualTo(Decision.DENY);
    }

    /** Docker and npm only sign in when challenged, and the answer is the same whether or not it exists. */
    @ParameterizedTest
    @EnumSource(Action.class)
    void anonymousCallersAreAskedToSignIn(Action action) {
        PermissionPolicy policy = policy(List.of(team(Role.ADMIN, Scope.organization())));
        Principal anonymous = new Principal.Anonymous();
        assertThat(policy.decide(anonymous, action, APP)).isEqualTo(Decision.AUTHENTICATE);
        assertThat(policy.decide(anonymous, action, Resource.organization())).isEqualTo(Decision.AUTHENTICATE);
        if (action != Action.PULL) {
            assertThat(policy.decide(anonymous, action, PUBLIC_APP)).isEqualTo(Decision.AUTHENTICATE);
        }
    }

    @Test
    void rolesAccumulateAcrossBindings() {
        PermissionPolicy policy =
                policy(List.of(team(Role.WRITER, Scope.namespace("acme")), team(Role.SECURITY, Scope.organization())));
        assertThat(policy.allowed(ADA, APP))
                .containsExactlyInAnyOrder(
                        Action.PULL, Action.PUSH, Action.BLOCK, Action.UNBLOCK, Action.VIEW_SECURITY);
        assertThat(policy.allowed(ADA, GLOBEX)).doesNotContain(Action.PUSH);
    }
}
