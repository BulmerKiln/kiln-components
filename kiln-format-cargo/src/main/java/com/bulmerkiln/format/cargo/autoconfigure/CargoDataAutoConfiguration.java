/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.cargo.repository.CargoVersionRepository;
import com.bulmerkiln.format.cargo.service.CargoAccessService;
import com.bulmerkiln.format.cargo.service.CargoCrateService;
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

/** Cargo's own table over the registry's, and who may do what, while the {@code /cargo/} routes exist. */
@AutoConfiguration(after = RegistryAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.cargo.enabled", matchIfMissing = true)
@EnableJdbcRepositories(basePackageClasses = CargoVersionRepository.class, enableDefaultTransactions = false)
public class CargoDataAutoConfiguration {

    @Bean
    CargoCrateService cargoCrateService(
            PackageService packages, BlobRecordService blobs, CargoVersionRepository versions) {
        return new CargoCrateService(packages, blobs, versions);
    }

    @Bean
    CargoAccessService cargoAccessService(
            Database database,
            NamespaceService namespaces,
            BlockService blocks,
            ArtifactAccess access,
            ArtifactPolicy policy) {
        return new CargoAccessService(database, namespaces, blocks, access, policy);
    }
}
