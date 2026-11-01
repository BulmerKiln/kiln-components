/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.format.oci.service.OciBlobService;
import com.bulmerkiln.format.oci.service.OciDeleteService;
import com.bulmerkiln.format.oci.service.OciManifestService;
import com.bulmerkiln.format.oci.service.OciTagService;
import com.bulmerkiln.format.oci.service.OciUploadService;
import com.bulmerkiln.format.oci.web.OciController;
import com.bulmerkiln.format.oci.web.OciRouteDispatcher;
import com.bulmerkiln.format.oci.web.handler.OciBlobHandler;
import com.bulmerkiln.format.oci.web.handler.OciManifestHandler;
import com.bulmerkiln.format.oci.web.handler.OciTagHandler;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /v2/} routes in a web application. With {@code kiln.format.oci.enabled=false}
 * the controller isn't created, nor are the services and tables behind it, so the routes don't exist
 * at all (FMT-OCI-30).
 */
@AutoConfiguration(after = OciServiceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.oci.enabled", matchIfMissing = true)
public class OciWebAutoConfiguration {

    @Bean
    OciController ociController(
            FormatRequests requests,
            OciManifestService manifests,
            OciDeleteService deletes,
            OciBlobService blobs,
            OciUploadService uploads,
            OciTagService tags,
            OciProperties properties) {
        return new OciController(new OciRouteDispatcher(
                requests,
                new OciManifestHandler(manifests, deletes, properties.maxManifestSizeOrDefault()),
                new OciBlobHandler(blobs, uploads),
                new OciTagHandler(tags)));
    }
}
