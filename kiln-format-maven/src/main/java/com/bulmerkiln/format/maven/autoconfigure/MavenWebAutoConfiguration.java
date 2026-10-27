/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileService;
import com.bulmerkiln.format.maven.service.MavenMetadataService;
import com.bulmerkiln.format.maven.store.MavenFileStore;
import com.bulmerkiln.format.maven.web.MavenController;
import com.bulmerkiln.format.maven.web.MavenRouteDispatcher;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /maven/} routes in a web application. With {@code
 * kiln.format.maven.enabled=false} the controller isn't created, so the routes don't exist at all
 * (FMT-MVN-16).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.maven.enabled", matchIfMissing = true)
@EnableConfigurationProperties(MavenProperties.class)
public class MavenWebAutoConfiguration {

    @Bean
    MavenController mavenController(
            FormatRequests requests,
            Database database,
            ArtifactAccess access,
            ArtifactPolicy policy,
            ArtifactEventService events,
            StorageRouter storage,
            MavenProperties properties) {
        MavenAccessService maven = new MavenAccessService(database, access, policy);
        MavenFileStore blobs = new MavenFileStore(storage, properties.maxFileSizeOrDefault());
        return new MavenController(new MavenRouteDispatcher(
                requests,
                new MavenFileService(database, maven, blobs, events),
                new MavenMetadataService(database, maven),
                maven));
    }
}
