/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.autoconfigure;

import com.bulmerkiln.storage.StorageBackend;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.service.SingleStoreRouter;
import com.bulmerkiln.storage.util.StorageBackendUtils;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides the {@link StorageRouter}: the selected backend's store for every organization. The
 * store isn't a bean of its own, so nothing can bypass the router. An assembly can provide its
 * own router, and then no store is created here.
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageRouter.class)
    StorageRouter storageRouter(StorageProperties properties, List<StorageBackend> backends) throws IOException {
        return new SingleStoreRouter(
                StorageBackendUtils.select(properties.getType(), backends).create());
    }
}
