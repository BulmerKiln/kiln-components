/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.bulmerkiln.core.org.Org;
import com.bulmerkiln.core.org.OrgId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StorageSelectionTest {

    private static final Org ACME = new Org(new OrgId(UUID.randomUUID()), "acme");

    private final InMemoryBlobStore memory = new InMemoryBlobStore();
    private final StorageBackend memoryBackend = backend("memory", memory);
    private final StorageBackend otherBackend = backend("other", new InMemoryBlobStore());

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
            .withBean("memoryBackend", StorageBackend.class, () -> memoryBackend)
            .withBean("otherBackend", StorageBackend.class, () -> otherBackend);

    @Test
    void selectsTheConfiguredBackend() throws Exception {
        assertThat(StorageBackends.select("memory", List.of(memoryBackend, otherBackend)))
                .isSameAs(memoryBackend);
        assertThat(StorageBackends.select("other", List.of(memoryBackend, otherBackend)))
                .isSameAs(otherBackend);
    }

    @Test
    void requiresAType() {
        assertThatIllegalStateException()
                .isThrownBy(() -> StorageBackends.select(null, List.of(memoryBackend, otherBackend)))
                .withMessage("kiln.storage.type must be set to one of [memory, other]");
        assertThatIllegalStateException().isThrownBy(() -> StorageBackends.select(" ", List.of(memoryBackend)));
    }

    @Test
    void namesTheAvailableBackendsForAnUnknownType() {
        assertThatIllegalStateException()
                .isThrownBy(() -> StorageBackends.select("s3", List.of(memoryBackend)))
                .withMessage("kiln.storage.type is s3, but the available backends are [memory]");
    }

    @Test
    void explainsAMissingBackend() {
        assertThatIllegalStateException()
                .isThrownBy(() -> StorageBackends.select("fs", List.of()))
                .withMessageContaining("kiln-storage-fs");
    }

    @Test
    void refusesTwoBackendsWithOneType() {
        assertThatIllegalStateException()
                .isThrownBy(() -> StorageBackends.select("memory", List.of(memoryBackend, backend("memory", memory))))
                .withMessageContaining("memory");
    }

    @Test
    void routesEveryOrganizationThroughTheSelectedStore() {
        runner.withPropertyValues("kiln.storage.type=memory").run(context -> {
            assertThat(context.getBean(StorageRouter.class).store(ACME)).isSameAs(memory);
            assertThat(context).doesNotHaveBean(BlobStore.class);
        });
    }

    @Test
    void failsTheBootWithoutAType() {
        runner.run(context ->
                assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("kiln.storage.type"));
    }

    @Test
    void createsNoStoreWhenARouterIsProvided() {
        StorageRouter custom = org -> memory;
        StorageBackend exploding = new StorageBackend() {
            @Override
            public String type() {
                return "exploding";
            }

            @Override
            public BlobStore create() {
                throw new AssertionError("must not be created");
            }
        };
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withBean(StorageBackend.class, () -> exploding)
                .withBean(StorageRouter.class, () -> custom)
                .withPropertyValues("kiln.storage.type=exploding")
                .run(context -> assertThat(context.getBean(StorageRouter.class)).isSameAs(custom));
    }

    private static StorageBackend backend(String type, BlobStore store) {
        return new StorageBackend() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public BlobStore create() {
                return store;
            }
        };
    }
}
