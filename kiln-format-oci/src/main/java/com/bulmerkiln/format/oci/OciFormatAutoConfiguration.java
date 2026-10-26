/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.format.FormatModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the oci format; {@code kiln.format.oci.enabled=false} switches it off. */
@AutoConfiguration
public class OciFormatAutoConfiguration {

    @Bean
    FormatModule ociFormat() {
        return new OciFormatModule();
    }
}
