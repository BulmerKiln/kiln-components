/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.format.FormatModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the cargo format; {@code kiln.format.cargo.enabled=false} switches it off. */
@AutoConfiguration
public class CargoFormatAutoConfiguration {

    @Bean
    FormatModule cargoFormat() {
        return new CargoFormatModule();
    }
}
