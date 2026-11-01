/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.autoconfigure;

import com.bulmerkiln.auth.user.service.AllowedDomainService;
import com.bulmerkiln.auth.user.service.IdentityLinkService;
import com.bulmerkiln.auth.user.service.InstanceAdminService;
import com.bulmerkiln.auth.user.service.MemberService;
import com.bulmerkiln.auth.user.service.SignInService;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.database.Database;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Users, members, instance admins and sign-in in the database. Bootstrap admins are reconciled
 * with {@code kiln.bootstrap} as the bean is created, after the allowed domains, since a bootstrap
 * email counts only through a connector that allows its domain.
 */
@AutoConfiguration(after = SignUpAutoConfiguration.class)
@ConditionalOnClass(Database.class)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class UserAutoConfiguration {

    private static final Clock CLOCK = Clock.systemUTC();

    @Bean
    @ConditionalOnMissingBean
    InstanceAdminService instanceAdminService(
            Database database, AdminBootstrapProperties bootstrap, AllowedDomainService reconciledFirst) {
        InstanceAdminService service = new InstanceAdminService(database, bootstrap.toBootstrapAdmins());
        service.reconcile();
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    SignInService signInService(
            Database database, OrganizationDirectory organizations, InstanceAdminService instanceAdmins) {
        return new SignInService(database, organizations, instanceAdmins, CLOCK);
    }

    @Bean
    @ConditionalOnMissingBean
    IdentityLinkService identityLinkService(Database database) {
        return new IdentityLinkService(database, CLOCK);
    }

    @Bean
    @ConditionalOnMissingBean
    MemberService memberService(Database database, OrganizationDirectory organizations) {
        return new MemberService(database, organizations);
    }
}
