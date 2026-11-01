/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Lets an encoded slash through to the formats, as npm sends scoped names as {@code @scope%2fname}
 * (WEB-4). Tomcat refuses one by default; each format parses its names strictly instead, and OCI
 * names refuse {@code %} entirely.
 */
@AutoConfiguration
@ConditionalOnClass(TomcatConnectorCustomizer.class)
public class WebTomcatAutoConfiguration {

    @Bean
    TomcatConnectorCustomizer kilnEncodedSlashes() {
        return connector -> connector.setEncodedSolidusHandling("passthrough");
    }
}
