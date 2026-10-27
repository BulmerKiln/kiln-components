/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.web.OciController;
import com.bulmerkiln.format.oci.web.OciRouteDispatcher;
import com.bulmerkiln.format.oci.web.handler.OciBlobHandler;
import com.bulmerkiln.format.oci.web.handler.OciManifestHandler;
import com.bulmerkiln.format.oci.web.handler.OciTagHandler;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /v2/} routes in a web application. With {@code kiln.format.oci.enabled=false}
 * the controller isn't created, so the routes don't exist at all (FMT-OCI-30).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.oci.enabled", matchIfMissing = true)
@EnableConfigurationProperties(OciProperties.class)
public class OciWebAutoConfiguration {

    @Bean
    OciController ociController(
            FormatRequests requests,
            Database database,
            ArtifactAccess access,
            ArtifactPolicy policy,
            ArtifactEventService events,
            StorageRouter storage,
            OciProperties properties) {
        OciAccessService oci = new OciAccessService(database, access, policy);
        return new OciController(new OciRouteDispatcher(
                requests,
                OciManifestHandler.of(database, oci, events, properties.maxManifestSizeOrDefault()),
                OciBlobHandler.of(database, oci, storage, properties.maxBlobSizeOrDefault()),
                OciTagHandler.of(database, oci, properties.tagPageSizeOrDefault())));
    }
}
