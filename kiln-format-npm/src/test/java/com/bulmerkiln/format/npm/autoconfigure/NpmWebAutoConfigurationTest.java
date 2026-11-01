/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.constant.Access;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.npm.web.NpmController;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.registry.autoconfigure.RegistryAutoConfiguration;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.fs.FsBlobStore;
import com.bulmerkiln.storage.service.SingleStoreRouter;
import com.bulmerkiln.web.FormatRequests;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** FMT-NPM-15: the routes exist only in a web application, and only while npm is enabled. */
class NpmWebAutoConfigurationTest {

    @TempDir
    Path blobs;

    @Test
    void registersTheRoutesByDefault() {
        withDependencies(new WebApplicationContextRunner())
                .run(context -> assertThat(context).hasSingleBean(NpmController.class));
    }

    @Test
    void registersNoRoutesWhenDisabled() {
        withDependencies(new WebApplicationContextRunner())
                .withPropertyValues("kiln.format.npm.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NpmController.class));
    }

    @Test
    void registersNoRoutesOutsideAWebApplication() {
        withDependencies(new ApplicationContextRunner())
                .run(context -> assertThat(context).doesNotHaveBean(NpmController.class));
    }

    @Test
    void readsItsLimit() {
        withDependencies(new WebApplicationContextRunner())
                .withPropertyValues("kiln.format.npm.max-tarball-size=5")
                .run(context -> assertThat(context.getBean(NpmProperties.class).maxTarballSizeOrDefault())
                        .isEqualTo(5));
    }

    private <R extends AbstractApplicationContextRunner<R, ?, ?>> R withDependencies(R runner) {
        ArtifactPolicy policy = new ArtifactPolicy() {
            @Override
            public Optional<Refusal> checkRead(ArtifactRef artifact) {
                return Optional.empty();
            }

            @Override
            public Optional<Refusal> checkWrite(ArtifactRef artifact, long bytes) {
                return Optional.empty();
            }
        };
        ArtifactEventListener ignored = new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {}

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {}
        };
        Function<Path, StorageRouter> router = root -> {
            try {
                return new SingleStoreRouter(new FsBlobStore(root));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
        return runner.withConfiguration(AutoConfigurations.of(
                        DatabaseAutoConfiguration.class,
                        DataJdbcAutoConfiguration.class,
                        RegistryAutoConfiguration.class,
                        NpmDataAutoConfiguration.class,
                        NpmServiceAutoConfiguration.class,
                        NpmWebAutoConfiguration.class))
                .withPropertyValues(KilnPostgres.propertyValues(KilnPostgres.newDatabase()))
                .withBean(
                        FormatRequests.class,
                        () -> new FormatRequests(
                                path -> Optional.empty(), request -> new Principal.Anonymous(), "http://localhost"))
                .withBean(ArtifactAccess.class, () -> (artifact, operation) -> Access.HIDE)
                .withBean(ArtifactPolicy.class, () -> policy)
                .withBean(ArtifactEventService.class, () -> new ArtifactEventService(List.of(ignored)))
                .withBean(StorageRouter.class, () -> router.apply(blobs));
    }
}
