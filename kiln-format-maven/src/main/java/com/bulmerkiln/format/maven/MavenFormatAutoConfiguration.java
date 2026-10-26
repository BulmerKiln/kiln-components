/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import com.bulmerkiln.format.FormatModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the maven format; {@code kiln.format.maven.enabled=false} switches it off. */
@AutoConfiguration
public class MavenFormatAutoConfiguration {

    @Bean
    FormatModule mavenFormat() {
        return new MavenFormatModule();
    }
}
