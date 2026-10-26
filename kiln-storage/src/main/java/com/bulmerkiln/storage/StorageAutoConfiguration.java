/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides the {@link StorageRouter}: the selected backend's store for every organization. The
 * store isn't a bean of its own, so nothing can bypass the router. kiln-cloud provides its own
 * router, and then no store is created here.
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageRouter.class)
    StorageRouter storageRouter(StorageProperties properties, List<StorageBackend> backends) throws IOException {
        return new SingleStoreRouter(
                StorageBackends.select(properties.type(), backends).create());
    }
}
