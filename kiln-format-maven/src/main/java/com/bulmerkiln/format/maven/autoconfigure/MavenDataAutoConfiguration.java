/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.maven.repository.MavenPluginRepository;
import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileStorageService;
import com.bulmerkiln.registry.autoconfigure.RegistryAutoConfiguration;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.storage.StorageRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * Maven's own table, its files in the blob store, and who may do what, while the {@code /maven/}
 * routes exist.
 */
@AutoConfiguration(after = RegistryAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.maven.enabled", matchIfMissing = true)
@EnableConfigurationProperties(MavenProperties.class)
@EnableJdbcRepositories(basePackageClasses = MavenPluginRepository.class, enableDefaultTransactions = false)
public class MavenDataAutoConfiguration {

    @Bean
    MavenAccessService mavenAccessService(
            Database database,
            NamespaceService namespaces,
            BlockService blocks,
            ArtifactAccess access,
            ArtifactPolicy policy) {
        return new MavenAccessService(database, namespaces, blocks, access, policy);
    }

    @Bean
    MavenFileStorageService mavenFileStorageService(
            Database database, BlobRecordService blobs, StorageRouter storage, MavenProperties properties) {
        return new MavenFileStorageService(database, blobs, storage, properties.maxFileSizeOrDefault());
    }
}
