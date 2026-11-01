/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.maven.repository.MavenPluginRepository;
import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileService;
import com.bulmerkiln.format.maven.service.MavenFileStorageService;
import com.bulmerkiln.format.maven.service.MavenMetadataService;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.PackageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Maven's operations, while the {@code /maven/} routes exist. */
@AutoConfiguration(after = MavenDataAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.maven.enabled", matchIfMissing = true)
public class MavenServiceAutoConfiguration {

    @Bean
    MavenFileService mavenFileService(
            Database database,
            MavenAccessService access,
            PackageService packages,
            BlobRecordService blobs,
            MavenFileStorageService storage,
            ArtifactEventService events) {
        return new MavenFileService(database, access, packages, blobs, storage, events);
    }

    @Bean
    MavenMetadataService mavenMetadataService(
            Database database, MavenAccessService access, PackageService packages, MavenPluginRepository plugins) {
        return new MavenMetadataService(database, access, packages, plugins);
    }
}
