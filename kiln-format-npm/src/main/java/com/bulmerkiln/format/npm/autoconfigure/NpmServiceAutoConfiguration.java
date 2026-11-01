/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.npm.service.NpmAccessService;
import com.bulmerkiln.format.npm.service.NpmPackageService;
import com.bulmerkiln.format.npm.service.NpmPublishService;
import com.bulmerkiln.format.npm.service.NpmReadService;
import com.bulmerkiln.format.npm.service.NpmTagService;
import com.bulmerkiln.format.npm.service.NpmUnpublishService;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.storage.StorageRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** npm's operations, while the {@code /npm/} routes exist. */
@AutoConfiguration(after = NpmDataAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.npm.enabled", matchIfMissing = true)
@EnableConfigurationProperties(NpmProperties.class)
public class NpmServiceAutoConfiguration {

    @Bean
    NpmReadService npmReadService(
            Database database,
            NpmAccessService access,
            NpmPackageService packages,
            StorageRouter storage,
            ArtifactEventService events) {
        return new NpmReadService(database, access, packages, storage, events);
    }

    @Bean
    NpmPublishService npmPublishService(
            Database database,
            NpmAccessService access,
            NpmPackageService packages,
            StorageRouter storage,
            ArtifactEventService events,
            NpmProperties properties) {
        return new NpmPublishService(database, access, packages, storage, events, properties.maxTarballSizeOrDefault());
    }

    @Bean
    NpmTagService npmTagService(Database database, NpmAccessService access, NpmPackageService packages) {
        return new NpmTagService(database, access, packages);
    }

    @Bean
    NpmUnpublishService npmUnpublishService(
            Database database, NpmAccessService access, NpmPackageService packages, BlockService blocks) {
        return new NpmUnpublishService(database, access, packages, blocks);
    }
}
