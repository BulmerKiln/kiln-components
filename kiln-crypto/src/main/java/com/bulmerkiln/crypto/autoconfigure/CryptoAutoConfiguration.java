/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.autoconfigure;

import com.bulmerkiln.crypto.KeyProvider;
import com.bulmerkiln.crypto.service.EnvelopeService;
import com.bulmerkiln.crypto.service.FileKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides {@link EnvelopeService} over the file KEK, unless the assembly provides its own
 * {@link KeyProvider} (CRYPTO-8).
 */
@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KeyProvider.class)
    KeyProvider fileKeyProvider(CryptoProperties properties) {
        return FileKeyProvider.load(properties.kekFileOrFail(), properties.generateKekIfMissingOrDefault());
    }

    @Bean
    EnvelopeService envelopeService(KeyProvider keys) {
        return new EnvelopeService(keys);
    }
}
