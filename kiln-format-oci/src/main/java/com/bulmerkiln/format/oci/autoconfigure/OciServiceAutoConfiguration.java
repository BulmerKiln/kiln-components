/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.service.OciBlobService;
import com.bulmerkiln.format.oci.service.OciDeleteService;
import com.bulmerkiln.format.oci.service.OciManifestRecordService;
import com.bulmerkiln.format.oci.service.OciManifestService;
import com.bulmerkiln.format.oci.service.OciRepositoryService;
import com.bulmerkiln.format.oci.service.OciTagService;
import com.bulmerkiln.format.oci.service.OciUploadService;
import com.bulmerkiln.format.oci.service.OciUploadStorageService;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.storage.StorageRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** OCI's operations, while the {@code /v2/} routes exist. */
@AutoConfiguration(after = OciAccessAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.oci.enabled", matchIfMissing = true)
public class OciServiceAutoConfiguration {

    @Bean
    OciManifestService ociManifestService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests,
            StorageRouter storage,
            ArtifactEventService events,
            OciProperties properties) {
        return new OciManifestService(
                database, access, repositories, manifests, storage, events, properties.maxManifestSizeOrDefault());
    }

    @Bean
    OciDeleteService ociDeleteService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests) {
        return new OciDeleteService(database, access, repositories, manifests);
    }

    @Bean
    OciBlobService ociBlobService(
            Database database, OciAccessService access, OciRepositoryService repositories, StorageRouter storage) {
        return new OciBlobService(database, access, repositories, storage);
    }

    @Bean
    OciUploadService ociUploadService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            BlobRecordService blobs,
            OciUploadStorageService storage) {
        return new OciUploadService(database, access, repositories, blobs, storage);
    }

    @Bean
    OciTagService ociTagService(
            Database database,
            OciAccessService access,
            OciRepositoryService repositories,
            OciManifestRecordService manifests,
            OciProperties properties) {
        return new OciTagService(database, access, repositories, manifests, properties.tagPageSizeOrDefault());
    }
}
