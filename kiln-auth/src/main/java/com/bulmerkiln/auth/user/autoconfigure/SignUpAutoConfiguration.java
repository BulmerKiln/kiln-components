/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.autoconfigure;

import com.bulmerkiln.auth.autoconfigure.ConnectorAutoConfiguration;
import com.bulmerkiln.auth.connector.ConnectorRegistry;
import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.util.DomainUtils;
import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import com.bulmerkiln.auth.user.service.AllowedDomainService;
import com.bulmerkiln.auth.user.service.InvitationService;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * How people who aren't users yet get in: invite links, and allowed domains reconciled with
 * {@code kiln.auth.allowed-domains} as the bean is created.
 */
@AutoConfiguration(after = {DatabaseAutoConfiguration.class, ConnectorAutoConfiguration.class})
@ConditionalOnClass(Database.class)
@EnableConfigurationProperties(SignUpProperties.class)
public class SignUpAutoConfiguration {

    private static final Clock CLOCK = Clock.systemUTC();

    @Bean
    @ConditionalOnMissingBean
    AllowedDomainService allowedDomainService(
            Database database,
            OrganizationDirectory organizations,
            ObjectProvider<ConnectorRegistry> connectors,
            SignUpProperties signUp) {
        AllowedDomainService service = new AllowedDomainService(database);
        service.reconcile(allowedDomains(signUp, organizations, connectors.getIfAvailable()));
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    InvitationService invitationService(Database database) {
        return new InvitationService(database, CLOCK);
    }

    /**
     * Validates {@code kiln.auth.allowed-domains}: a known connector, a plain domain and an existing
     * organization, or the boot stops with an error naming the setting.
     */
    static List<AllowedDomainEntity> allowedDomains(
            SignUpProperties signUp, OrganizationDirectory organizations, @Nullable ConnectorRegistry connectors) {
        return signUp.allowedDomainsOrNone().stream()
                .map(entry -> {
                    ConnectorId connector = new ConnectorId(required(entry.getConnector(), "connector"));
                    if (connectors != null && connectors.get(connector).isEmpty()) {
                        throw new IllegalArgumentException(
                                "kiln.auth.allowed-domains names connector " + connector + ", which isn't configured");
                    }
                    String domain = DomainUtils.normalize(List.of(required(entry.getDomain(), "domain")))
                            .iterator()
                            .next();
                    String slug = required(entry.getOrganization(), "organization");
                    var organization = organizations
                            .bySlug(slug)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "kiln.auth.allowed-domains names organization " + slug + ", which doesn't exist"));
                    return new AllowedDomainEntity(
                            connector.getValue(), domain, organization.getId().getValue(), AllowedDomainSource.CONFIG);
                })
                .toList();
    }

    private static String required(@Nullable String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Every kiln.auth.allowed-domains entry needs a " + setting);
        }
        return value;
    }
}
