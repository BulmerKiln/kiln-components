/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileService;
import com.bulmerkiln.format.maven.service.MavenMetadataService;
import com.bulmerkiln.format.maven.web.MavenController;
import com.bulmerkiln.format.maven.web.MavenRouteDispatcher;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /maven/} routes in a web application. With {@code
 * kiln.format.maven.enabled=false} the controller isn't created, nor are the services and
 * tables behind it, so the routes don't exist at all
 * (FMT-MVN-16).
 */
@AutoConfiguration(after = MavenServiceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.maven.enabled", matchIfMissing = true)
public class MavenWebAutoConfiguration {

    @Bean
    MavenController mavenController(
            FormatRequests requests, MavenAccessService access, MavenFileService files, MavenMetadataService metadata) {
        return new MavenController(new MavenRouteDispatcher(requests, files, metadata, access));
    }
}
