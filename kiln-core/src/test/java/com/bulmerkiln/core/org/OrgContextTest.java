/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.org;

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

class OrgContextTest {

    private final Org acme = new Org(new OrgId(UUID.randomUUID()), "acme");
    private final Org globex = new Org(new OrgId(UUID.randomUUID()), "globex");

    @Test
    void failsOutsideAScope() {
        assertThat(OrgContext.isBound()).isFalse();
        assertThatIllegalStateException().isThrownBy(OrgContext::current);
    }

    @Test
    void bindsForTheScopeOnly() {
        assertThat(OrgContext.call(acme, OrgContext::current)).isEqualTo(acme);
        assertThat(OrgContext.isBound()).isFalse();
    }

    @Test
    void refusesToSwitchOrganizationInsideAScope() {
        OrgContext.run(
                acme,
                () -> assertThatIllegalStateException()
                        .isThrownBy(() -> OrgContext.run(globex, () -> {}))
                        .withMessageContaining("globex"));
    }

    @Test
    void allowsReenteringTheSameOrganization() {
        OrgContext.run(
                acme,
                () -> assertThat(OrgContext.call(acme, OrgContext::current)).isEqualTo(acme));
    }

    @Test
    void unbindsWhenTheScopeThrows() {
        assertThatThrownBy(() -> OrgContext.run(acme, () -> {
                    throw new IllegalArgumentException("boom");
                }))
                .hasMessage("boom");
        assertThat(OrgContext.isBound()).isFalse();
    }

    @Test
    void propagatesCheckedExceptions() {
        assertThatThrownBy(() -> OrgContext.call(acme, () -> {
                    throw new IOException("disk");
                }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void doesNotLeakIntoThreadsStartedInsideTheScope() throws Exception {
        AtomicReference<Boolean> boundInThread = new AtomicReference<>();
        OrgContext.run(acme, () -> {
            Thread thread = Thread.ofVirtual().start(() -> boundInThread.set(OrgContext.isBound()));
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(boundInThread.get()).isFalse();

        CompletableFuture<Boolean> async =
                OrgContext.call(acme, () -> CompletableFuture.supplyAsync(OrgContext::isBound));
        assertThat(async.get()).isFalse();
    }

    @Test
    void concurrentScopesSeeOnlyTheirOwnOrganization() throws Exception {
        List<Org> orgs = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            orgs.add(new Org(new OrgId(UUID.randomUUID()), "org-" + i));
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Org org : orgs) {
                results.add(executor.submit(() -> OrgContext.call(org, () -> {
                    start.await();
                    return OrgContext.current().equals(org);
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
        Org impostor = new Org(new OrgId(UUID.randomUUID()), "acme");
        OrgContext.run(
                acme, () -> assertThatIllegalStateException().isThrownBy(() -> OrgContext.run(impostor, () -> {})));
    }
}
