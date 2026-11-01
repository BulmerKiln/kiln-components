/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Principal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistryDomainTest {

    private static final ArtifactFormat OCI = new ArtifactFormat("oci");

    /** A version is published by a member or a service account; workloads and anonymous callers aren't recorded yet. */
    @Test
    void recordsMembersAndServiceAccountsAsPublishers() {
        UUID member = UUID.randomUUID();
        UUID account = UUID.randomUUID();

        assertThat(Publisher.of(new Principal.Member(new MemberId(member), Set.of())))
                .contains(new Publisher("member", member));
        assertThat(Publisher.of(new Principal.ServiceAccount(new ServiceAccountId(account))))
                .contains(new Publisher("service_account", account));
        assertThat(Publisher.of(new Principal.Workload("github:acme/api", Set.of())))
                .isEmpty();
        assertThat(Publisher.of(new Principal.Anonymous())).isEmpty();
    }

    /** REG-1: a claim has a value, and a separator only if it's non-empty. */
    @Test
    void refusesEmptyClaims() {
        assertThat(new NamespaceClaim(OCI, "acme", "/").getSeparator()).isEqualTo("/");
        assertThat(new NamespaceClaim(OCI, "acme-", null).getSeparator()).isNull();
        assertThatThrownBy(() -> new NamespaceClaim(OCI, "", "/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NamespaceClaim(OCI, "acme", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNamespaceHasAName() {
        assertThatThrownBy(() -> new Namespace(UUID.randomUUID(), " ", Visibility.PRIVATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
