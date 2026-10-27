/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.npm.service.NpmAccessService;
import com.bulmerkiln.format.npm.service.NpmPublishService;
import com.bulmerkiln.format.npm.service.NpmReadService;
import com.bulmerkiln.format.npm.service.NpmTagService;
import com.bulmerkiln.format.npm.web.NpmController;
import com.bulmerkiln.format.npm.web.NpmRouteDispatcher;
import com.bulmerkiln.format.npm.web.handler.NpmPackageHandler;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /npm/} routes in a web application. With {@code kiln.format.npm.enabled=false}
 * the controller isn't created, so the routes don't exist at all (FMT-NPM-15).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.npm.enabled", matchIfMissing = true)
@EnableConfigurationProperties(NpmProperties.class)
public class NpmWebAutoConfiguration {

    @Bean
    NpmController npmController(
            FormatRequests requests,
            Database database,
            ArtifactAccess access,
            ArtifactPolicy policy,
            ArtifactEventService events,
            StorageRouter storage,
            NpmProperties properties) {
        NpmAccessService npm = new NpmAccessService(database, access, policy);
        return new NpmController(new NpmRouteDispatcher(
                requests,
                new NpmPackageHandler(
                        new NpmReadService(database, npm, storage, events),
                        new NpmPublishService(database, npm, storage, events, properties.maxTarballSizeOrDefault()),
                        new NpmTagService(database, npm))));
    }
}
