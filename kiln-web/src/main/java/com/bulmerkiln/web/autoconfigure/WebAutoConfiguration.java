/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.autoconfigure;

import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.web.FormatRequests;
import com.bulmerkiln.web.PrincipalResolver;
import com.bulmerkiln.web.service.AnonymousPrincipalResolver;
import com.bulmerkiln.web.service.PathOrganizationResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides {@link FormatRequests}, resolving organizations by path from the {@link
 * OrganizationDirectory}. {@code kiln.web.external-url} must be set, and a directory must exist;
 * without either Kiln doesn't start. Callers are
 * anonymous until a {@link PrincipalResolver} replaces the default (WEB-3).
 */
@AutoConfiguration
@EnableConfigurationProperties(WebProperties.class)
public class WebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PrincipalResolver.class)
    PrincipalResolver anonymousPrincipalResolver() {
        return new AnonymousPrincipalResolver();
    }

    /** Organizations by the first path segment, unless the service provides its own resolver (WEB-6). */
    @Bean
    @ConditionalOnMissingBean(OrganizationResolver.class)
    OrganizationResolver pathOrganizationResolver(OrganizationDirectory directory) {
        return new PathOrganizationResolver(directory);
    }

    @Bean
    FormatRequests formatRequests(
            OrganizationResolver organizations, PrincipalResolver principals, WebProperties properties) {
        return new FormatRequests(organizations, principals, properties.externalUrlOrFail());
    }
}
