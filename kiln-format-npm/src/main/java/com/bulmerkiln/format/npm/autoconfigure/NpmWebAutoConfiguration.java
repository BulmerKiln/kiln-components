/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import com.bulmerkiln.format.npm.service.NpmPublishService;
import com.bulmerkiln.format.npm.service.NpmReadService;
import com.bulmerkiln.format.npm.service.NpmTagService;
import com.bulmerkiln.format.npm.service.NpmUnpublishService;
import com.bulmerkiln.format.npm.web.NpmController;
import com.bulmerkiln.format.npm.web.NpmRouteDispatcher;
import com.bulmerkiln.format.npm.web.handler.NpmPackageHandler;
import com.bulmerkiln.format.npm.web.handler.NpmUnpublishHandler;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /npm/} routes in a web application. With {@code
 * kiln.format.npm.enabled=false} the controller isn't created, nor are the services and tables
 * behind it, so the routes don't exist at all (FMT-NPM-15).
 */
@AutoConfiguration(after = NpmServiceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.npm.enabled", matchIfMissing = true)
public class NpmWebAutoConfiguration {

    @Bean
    NpmController npmController(
            FormatRequests requests,
            NpmReadService reads,
            NpmPublishService publisher,
            NpmTagService tags,
            NpmUnpublishService unpublish) {
        return new NpmController(new NpmRouteDispatcher(
                requests, new NpmPackageHandler(reads, publisher, tags), new NpmUnpublishHandler(unpublish)));
    }
}
