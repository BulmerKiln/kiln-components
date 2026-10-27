/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.permissions.RoleBindingRepository;
import com.bulmerkiln.permissions.autoconfigure.PermissionsAutoConfiguration;
import com.bulmerkiln.permissions.autoconfigure.StoredPermissionsAutoConfiguration;
import com.bulmerkiln.permissions.constant.Action;
import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Ids.TeamId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.Resource;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.service.RoleBasedPolicy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** PERM-13: role bindings kept in PostgreSQL, per organization. */
class RoleBindingStoreTest {

    private static final MemberId ALICE = new MemberId(UUID.randomUUID());
    private static final MemberId BOB = new MemberId(UUID.randomUUID());
    private static final TeamId PLATFORM = new TeamId(UUID.randomUUID());
    private static final ServiceAccountId CI = new ServiceAccountId(UUID.randomUUID());

    private static Database database;
    private static RoleBindingStore bindings;
    private static Organization acme;
    private static Organization globex;

    @BeforeAll
    static void start() {
        database = KilnPostgres.database();
        bindings = new RoleBindingStore(database);
        acme = KilnPostgres.organization(database, "acme");
        globex = KilnPostgres.organization(database, "globex");
        OrganizationContext.run(acme, () -> {
            bindings.add(new RoleBinding(new RoleBinding.Subject.Member(ALICE), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(new RoleBinding.Subject.Member(ALICE), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(new RoleBinding.Subject.Team(PLATFORM), Role.ADMIN, Scope.organization()));
            bindings.add(
                    new RoleBinding(new RoleBinding.Subject.Member(BOB), Role.READER, Scope.repository("acme", "api")));
            bindings.add(
                    new RoleBinding(new RoleBinding.Subject.ServiceAccount(CI), Role.WRITER, Scope.namespace("ci")));
        });
    }

    @AfterAll
    static void stop() {
        database.close();
    }

    @Test
    void readsAMembersOwnAndTheirTeamsBindings() {
        Principal alice = new Principal.Member(ALICE, Set.of(PLATFORM));

        assertThat(OrganizationContext.call(acme, () -> bindings.bindingsFor(alice)))
                .containsExactlyInAnyOrder(
                        new RoleBinding(new RoleBinding.Subject.Member(ALICE), Role.WRITER, Scope.namespace("acme")),
                        new RoleBinding(new RoleBinding.Subject.Team(PLATFORM), Role.ADMIN, Scope.organization()));
    }

    @Test
    void readsOnlyThePrincipalsBindings() {
        Principal bob = new Principal.Member(BOB, Set.of());
        Principal ci = new Principal.ServiceAccount(CI);

        assertThat(OrganizationContext.call(acme, () -> bindings.bindingsFor(bob)))
                .containsExactly(new RoleBinding(
                        new RoleBinding.Subject.Member(BOB), Role.READER, Scope.repository("acme", "api")));
        assertThat(OrganizationContext.call(acme, () -> bindings.bindingsFor(ci)))
                .containsExactly(new RoleBinding(
                        new RoleBinding.Subject.ServiceAccount(CI), Role.WRITER, Scope.namespace("ci")));
    }

    @Test
    void givesWorkloadsAndAnonymousCallersNone() {
        assertThat(OrganizationContext.call(acme, () -> bindings.bindingsFor(new Principal.Anonymous())))
                .isEmpty();
        assertThat(OrganizationContext.call(
                        acme, () -> bindings.bindingsFor(new Principal.Workload("github", Set.of()))))
                .isEmpty();
    }

    /** Bindings belong to one organization; the same member elsewhere holds nothing. */
    @Test
    void keepsBindingsPerOrganization() {
        Principal alice = new Principal.Member(ALICE, Set.of(PLATFORM));

        assertThat(OrganizationContext.call(globex, () -> bindings.bindingsFor(alice)))
                .isEmpty();
    }

    @Test
    void decidesFromStoredBindings() {
        RoleBasedPolicy policy = new RoleBasedPolicy(bindings);
        Principal bob = new Principal.Member(BOB, Set.of());
        Resource api = new Resource(Scope.repository("acme", "api"), Visibility.PRIVATE);

        assertThat(OrganizationContext.call(acme, () -> policy.allowed(bob, api)))
                .containsExactly(Action.PULL);
    }

    @Test
    void theSchemaIsOrganizationSafe() {
        assertThat(database.instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    @Test
    void keepsBindingsInTheDatabaseWhenItIsPresent() {
        // Its own database: the context closes the one it's given.
        Database own = KilnPostgres.database();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        StoredPermissionsAutoConfiguration.class, PermissionsAutoConfiguration.class))
                .withBean(Database.class, () -> own)
                .run(context ->
                        assertThat(context.getBean(RoleBindingRepository.class)).isInstanceOf(RoleBindingStore.class));
    }
}
