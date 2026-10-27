/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrganizationContextTest {

    private final Organization acme = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    private final Organization globex = new Organization(new OrganizationId(UUID.randomUUID()), "globex");

    @Test
    void failsOutsideAScope() {
        assertThat(OrganizationContext.isBound()).isFalse();
        assertThatIllegalStateException().isThrownBy(OrganizationContext::current);
    }

    @Test
    void bindsForTheScopeOnly() {
        assertThat(OrganizationContext.call(acme, OrganizationContext::current)).isEqualTo(acme);
        assertThat(OrganizationContext.isBound()).isFalse();
    }

    @Test
    void refusesToSwitchOrganizationInsideAScope() {
        OrganizationContext.run(
                acme,
                () -> assertThatIllegalStateException()
                        .isThrownBy(() -> OrganizationContext.run(globex, () -> {}))
                        .withMessageContaining("globex"));
    }

    @Test
    void allowsReenteringTheSameOrganization() {
        OrganizationContext.run(
                acme,
                () -> assertThat(OrganizationContext.call(acme, OrganizationContext::current))
                        .isEqualTo(acme));
    }

    @Test
    void unbindsWhenTheScopeThrows() {
        assertThatThrownBy(() -> OrganizationContext.run(acme, () -> {
                    throw new IllegalArgumentException("boom");
                }))
                .hasMessage("boom");
        assertThat(OrganizationContext.isBound()).isFalse();
    }

    @Test
    void propagatesCheckedExceptions() {
        assertThatThrownBy(() -> OrganizationContext.call(acme, () -> {
                    throw new IOException("disk");
                }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void doesNotLeakIntoThreadsStartedInsideTheScope() throws Exception {
        AtomicReference<Boolean> boundInThread = new AtomicReference<>();
        OrganizationContext.run(acme, () -> {
            Thread thread = Thread.ofVirtual().start(() -> boundInThread.set(OrganizationContext.isBound()));
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(boundInThread.get()).isFalse();

        CompletableFuture<Boolean> async =
                OrganizationContext.call(acme, () -> CompletableFuture.supplyAsync(OrganizationContext::isBound));
        assertThat(async.get()).isFalse();
    }

    @Test
    void concurrentScopesSeeOnlyTheirOwnOrganization() throws Exception {
        List<Organization> organizations = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            organizations.add(new Organization(new OrganizationId(UUID.randomUUID()), "organization-" + i));
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Organization organization : organizations) {
                results.add(executor.submit(() -> OrganizationContext.call(organization, () -> {
                    start.await();
                    return OrganizationContext.current().equals(organization);
                })));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        }
    }

    @Test
    void organizationsWithTheSameSlugButDifferentIdsAreDifferent() {
        Organization impostor = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
        OrganizationContext.run(
                acme,
                () -> assertThatIllegalStateException().isThrownBy(() -> OrganizationContext.run(impostor, () -> {})));
    }
}
