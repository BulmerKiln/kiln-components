/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.cargo.service.CargoAccessService;
import com.bulmerkiln.format.cargo.service.CargoCrateService;
import com.bulmerkiln.format.cargo.service.CargoPublishService;
import com.bulmerkiln.format.cargo.service.CargoReadService;
import com.bulmerkiln.format.cargo.web.CargoController;
import com.bulmerkiln.format.cargo.web.CargoRouteDispatcher;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.web.FormatRequests;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code /cargo/} routes in a web application. With {@code
 * kiln.format.cargo.enabled=false} the controller isn't created, nor are the services and
 * tables behind it, so the routes don't exist at all
 * (FMT-CARGO-13).
 */
@AutoConfiguration(after = CargoDataAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "kiln.format.cargo.enabled", matchIfMissing = true)
@EnableConfigurationProperties(CargoProperties.class)
public class CargoWebAutoConfiguration {

    @Bean
    CargoController cargoController(
            FormatRequests requests,
            Database database,
            CargoAccessService access,
            CargoCrateService crates,
            ArtifactEventService events,
            StorageRouter storage,
            CargoProperties properties) {
        return new CargoController(new CargoRouteDispatcher(
                requests,
                new CargoReadService(database, access, crates, storage, events),
                new CargoPublishService(
                        database, access, crates, storage, events, properties.maxCrateSizeOrDefault())));
    }
}
