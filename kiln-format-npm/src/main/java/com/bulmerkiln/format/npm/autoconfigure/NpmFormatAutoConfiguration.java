/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.npm.NpmFormatModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the npm format; {@code kiln.format.npm.enabled=false} switches it off. */
@AutoConfiguration
public class NpmFormatAutoConfiguration {

    @Bean
    FormatModule npmFormat() {
        return new NpmFormatModule();
    }
}
