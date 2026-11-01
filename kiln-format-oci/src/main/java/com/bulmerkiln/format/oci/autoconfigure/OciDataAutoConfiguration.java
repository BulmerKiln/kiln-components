/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.autoconfigure;

import com.bulmerkiln.format.oci.repository.OciManifestRefRepository;
import com.bulmerkiln.format.oci.repository.OciManifestRepository;
import com.bulmerkiln.format.oci.repository.OciRepositoryBlobRepository;
import com.bulmerkiln.format.oci.repository.OciTagRepository;
import com.bulmerkiln.format.oci.repository.OciUploadSessionRepository;
import com.bulmerkiln.format.oci.service.OciManifestRecordService;
import com.bulmerkiln.format.oci.service.OciRepositoryService;
import com.bulmerkiln.registry.autoconfigure.RegistryAutoConfiguration;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/** OCI's own tables over the registry's, while the {@code /v2/} routes exist. */
@AutoConfiguration(after = RegistryAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.oci.enabled", matchIfMissing = true)
@EnableJdbcRepositories(basePackageClasses = OciManifestRepository.class, enableDefaultTransactions = false)
public class OciDataAutoConfiguration {

    @Bean
    OciRepositoryService ociRepositoryService(
            PackageService packages, OciRepositoryBlobRepository blobs, OciUploadSessionRepository uploads) {
        return new OciRepositoryService(packages, blobs, uploads);
    }

    @Bean
    OciManifestRecordService ociManifestRecordService(
            PackageService packages,
            BlobRecordService blobs,
            OciManifestRepository manifests,
            OciManifestRefRepository refs,
            OciTagRepository tags,
            OciRepositoryBlobRepository links) {
        return new OciManifestRecordService(packages, blobs, manifests, refs, tags, links);
    }
}
