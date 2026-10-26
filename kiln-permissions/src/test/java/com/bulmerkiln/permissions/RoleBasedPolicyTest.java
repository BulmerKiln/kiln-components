/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.Access;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactRef;
import com.bulmerkiln.format.Operation;
import com.bulmerkiln.permissions.Ids.MemberId;
import com.bulmerkiln.permissions.Ids.TeamId;
import com.bulmerkiln.permissions.TeamMembership.GroupMapping;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RoleBasedPolicyTest extends PermissionPolicyContract {

    @Override
    protected PermissionPolicy policy(List<RoleBinding> bindings) {
        return new RoleBasedPolicy(principal -> bindings);
    }

    @Test
    void aRepositoryCannotGrantBindingsThatDoNotApply() {
        // Even if the data layer returns another member's admin binding, it must not count.
        RoleBinding someoneElses = new RoleBinding(
                new RoleBinding.Subject.Member(new MemberId(UUID.randomUUID())), Role.ADMIN, Scope.organization());
        PermissionPolicy policy = new RoleBasedPolicy(principal -> List.of(someoneElses));
        assertThat(policy.allowed(BOB, APP)).isEmpty();
    }

    @Test
    void mapsGroupsToTeamsPerConnector() {
        TeamId admins = new TeamId(UUID.randomUUID());
        TeamId devs = new TeamId(UUID.randomUUID());
        List<GroupMapping> mappings =
                List.of(new GroupMapping(admins, "okta", "kiln-admins"), new GroupMapping(devs, "github", "acme/dev"));

        assertThat(TeamMembership.resolve("okta", Set.of("kiln-admins", "other"), mappings, Set.of()))
                .containsExactly(admins);
        // The same group name from another IdP means nothing.
        assertThat(TeamMembership.resolve("github", Set.of("kiln-admins"), mappings, Set.of()))
                .isEmpty();
        assertThat(TeamMembership.resolve("okta", Set.of("Kiln-Admins"), mappings, Set.of()))
                .isEmpty();
    }

    @Test
    void noGroupsLeavesOnlyExplicitTeams() {
        TeamId explicit = new TeamId(UUID.randomUUID());
        List<GroupMapping> mappings = List.of(new GroupMapping(new TeamId(UUID.randomUUID()), "okta", "kiln-admins"));
        assertThat(TeamMembership.resolve("okta", Set.of(), mappings, Set.of())).isEmpty();
        assertThat(TeamMembership.resolve("okta", Set.of(), mappings, Set.of(explicit)))
                .containsExactly(explicit);
    }

    @Test
    void scopesAreWellFormed() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Scope(null, "app"));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.namespace(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.repository("acme", ""));
        assertThat(Scope.organization().covers(Scope.repository("a", "b"))).isTrue();
        assertThat(Scope.namespace("a").covers(Scope.organization())).isFalse();
        assertThat(Scope.repository("a", "b").covers(Scope.namespace("a"))).isFalse();
    }

    @Test
    void thePrincipalMustBeBoundAndNeverSwitches() {
        assertThatIllegalStateException().isThrownBy(CurrentPrincipal::get);
        CurrentPrincipal.run(BOB, () -> {
            assertThat(CurrentPrincipal.get()).isEqualTo(BOB);
            assertThatIllegalStateException().isThrownBy(() -> CurrentPrincipal.run(ADA, () -> {}));
        });
    }

    @Test
    void answersTheFormatAccessPort() {
        PermissionPolicy policy = policy(
                List.of(new RoleBinding(new RoleBinding.Subject.Team(PLATFORM), Role.READER, Scope.namespace("acme"))));
        FormatAccess access = new FormatAccess(policy, scope -> Visibility.PRIVATE);
        ArtifactFormat oci = new ArtifactFormat("oci");

        CurrentPrincipal.run(ADA, () -> {
            assertThat(access.check(new ArtifactRef(oci, "acme", "app", "1.0"), Operation.PULL))
                    .isEqualTo(Access.ALLOW);
            assertThat(access.check(new ArtifactRef(oci, "acme", "app", "1.0"), Operation.PUSH))
                    .isEqualTo(Access.DENY);
            assertThat(access.check(new ArtifactRef(oci, "globex", "app", "1.0"), Operation.PULL))
                    .isEqualTo(Access.HIDE);
        });
        CurrentPrincipal.run(
                new Principal.Anonymous(),
                () -> assertThat(access.check(new ArtifactRef(oci, "acme", "app", null), Operation.PULL))
                        .isEqualTo(Access.AUTHENTICATE));
    }

    @Test
    void refusesToStartWithoutStoredBindings() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PermissionsAutoConfiguration.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void wiresThePolicyAndTheFormatPort() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PermissionsAutoConfiguration.class, FormatAccessAutoConfiguration.class))
                .withBean(RoleBindingRepository.class, () -> principal -> List.of())
                .withBean(VisibilityLookup.class, () -> scope -> Visibility.PRIVATE)
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionPolicy.class).hasSingleBean(ArtifactAccess.class);
                    assertThat(context.getBean(ArtifactAccess.class)).isInstanceOf(FormatAccess.class);
                });
    }
}
