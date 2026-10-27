/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.storage.StorageAutoConfiguration;
import com.bulmerkiln.storage.StorageRouter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FsStorageAutoConfigurationTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class, FsStorageAutoConfiguration.class));

    @TempDir
    Path root;

    @Test
    void routesToTheFilesystemStoreWhenSelected() {
        runner.withPropertyValues("kiln.storage.type=fs", "kiln.storage.fs.root=" + root.resolve("blobs"))
                .run(context -> {
                    assertThat(context.getBean(StorageRouter.class).store(ACME)).isInstanceOf(FsBlobStore.class);
                    assertThat(root.resolve("blobs")).isDirectory();
                });
    }

    @Test
    void failsClearlyWithoutARoot() {
        runner.withPropertyValues("kiln.storage.type=fs")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("kiln.storage.fs.root"));
    }

    @Test
    void touchesNothingWhenAnotherBackendIsSelected() {
        runner.withPropertyValues("kiln.storage.type=s3", "kiln.storage.fs.root=" + root.resolve("unused"))
                .run(context -> {
                    assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("[fs]");
                    assertThat(root.resolve("unused")).doesNotExist();
                });
    }

    @Test
    void refusesARootThatCannotBeUsed() throws Exception {
        Path file = Files.writeString(root.resolve("file"), "x");
        runner.withPropertyValues("kiln.storage.type=fs", "kiln.storage.fs.root=" + file)
                .run(context -> assertThat(context).hasFailed());
    }
}
