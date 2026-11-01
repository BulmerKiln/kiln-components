/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.registry.mapper.NamespaceMapperImpl;
import com.bulmerkiln.registry.mapper.PackageMapperImpl;
import com.bulmerkiln.registry.repository.BlobRepository;
import com.bulmerkiln.registry.repository.BlockRepository;
import com.bulmerkiln.registry.repository.NamespaceClaimRepository;
import com.bulmerkiln.registry.repository.NamespaceRepository;
import com.bulmerkiln.registry.repository.PackageFileRepository;
import com.bulmerkiln.registry.repository.PackageRepository;
import com.bulmerkiln.registry.repository.PackageVersionRepository;
import com.bulmerkiln.registry.repository.TombstoneRepository;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.registry.service.PackageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/** The registry's tables, and the services formats store through. */
@AutoConfiguration(after = DataJdbcAutoConfiguration.class)
@EnableJdbcRepositories(basePackageClasses = PackageRepository.class, enableDefaultTransactions = false)
public class RegistryAutoConfiguration {

    @Bean
    NamespaceService namespaceService(NamespaceRepository namespaces, NamespaceClaimRepository claims) {
        return new NamespaceService(namespaces, claims, new NamespaceMapperImpl());
    }

    @Bean
    PackageService packageService(
            PackageRepository packages, PackageVersionRepository versions, PackageFileRepository files) {
        return new PackageService(packages, versions, files, new PackageMapperImpl());
    }

    @Bean
    BlobRecordService blobRecordService(BlobRepository blobs) {
        return new BlobRecordService(blobs, new PackageMapperImpl());
    }

    @Bean
    BlockService blockService(BlockRepository blocks, TombstoneRepository tombstones) {
        return new BlockService(blocks, tombstones);
    }
}
