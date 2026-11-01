/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.service.OciUploadStorageService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.storage.StorageRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Who may do what to a repository, and OCI's blob store, while the {@code /v2/} routes exist. */
@AutoConfiguration(after = OciDataAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.oci.enabled", matchIfMissing = true)
@EnableConfigurationProperties(OciProperties.class)
public class OciAccessAutoConfiguration {

    @Bean
    OciAccessService ociAccessService(
            Database database,
            NamespaceService namespaces,
            BlockService blocks,
            ArtifactAccess access,
            ArtifactPolicy policy) {
        return new OciAccessService(database, namespaces, blocks, access, policy);
    }

    @Bean
    OciUploadStorageService ociUploadStorageService(StorageRouter storage, OciProperties properties) {
        return new OciUploadStorageService(storage, properties.maxBlobSizeOrDefault());
    }
}
