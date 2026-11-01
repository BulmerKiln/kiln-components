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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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

    static final Set<String> MANDATORY_HEADERS =
            Set.of("Authorization", "Kiln-Organization", "Kiln-Format", "Content-Type");
    static final Set<String> MANDATORY_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Bean
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Null-checked before use")
    CorsFilter corsFilter(WebProperties properties) {
        WebProperties.Cors cors =
                properties.getCors() == null ? new WebProperties.Cors(null, null, null) : properties.getCors();
        List<String> origins = cors.getAllowedOrigins() == null ? List.of() : cors.getAllowedOrigins();
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(MANDATORY_HEADERS);
        if (cors.getAllowedHeaders() != null) {
            headers.addAll(cors.getAllowedHeaders());
        }
        Set<String> methods = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        methods.addAll(MANDATORY_METHODS);
        if (cors.getAllowedMethods() != null) {
            methods.addAll(cors.getAllowedMethods());
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedHeaders(List.copyOf(headers));
        config.setAllowedMethods(List.copyOf(methods));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
