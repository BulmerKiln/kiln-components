/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.npm.repository.NpmDistTagRepository;
import com.bulmerkiln.format.npm.repository.NpmVersionRepository;
import com.bulmerkiln.format.npm.service.NpmAccessService;
import com.bulmerkiln.format.npm.service.NpmPackageService;
import com.bulmerkiln.registry.autoconfigure.RegistryAutoConfiguration;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.registry.service.PackageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/** npm's own tables over the registry's, and who may do what, while the {@code /npm/} routes exist. */
@AutoConfiguration(after = RegistryAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.npm.enabled", matchIfMissing = true)
@EnableJdbcRepositories(basePackageClasses = NpmVersionRepository.class, enableDefaultTransactions = false)
public class NpmDataAutoConfiguration {

    @Bean
    NpmPackageService npmPackageService(
            PackageService packages,
            BlobRecordService blobs,
            NpmVersionRepository documents,
            NpmDistTagRepository tags) {
        return new NpmPackageService(packages, blobs, documents, tags);
    }

    @Bean
    NpmAccessService npmAccessService(
            Database database,
            NamespaceService namespaces,
            BlockService blocks,
            ArtifactAccess access,
            ArtifactPolicy policy) {
        return new NpmAccessService(database, namespaces, blocks, access, policy);
    }
}
