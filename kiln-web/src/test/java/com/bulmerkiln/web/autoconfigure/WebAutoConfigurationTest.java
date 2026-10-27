/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.web.FormatRequests;
import com.bulmerkiln.web.PrincipalResolver;
import com.bulmerkiln.web.service.PathOrganizationResolver;
import java.util.List;
import java.util.Optional;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.mock.web.MockHttpServletRequest;

class WebAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class, WebTomcatAutoConfiguration.class))
            .withPropertyValues("kiln.web.external-url=https://pkg.example.com/");

    private static final OrganizationResolver NONE = path -> Optional.empty();

    private static final OrganizationDirectory NO_ORGANIZATIONS = new OrganizationDirectory() {
        @Override
        public Optional<Organization> byId(OrganizationId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Organization> bySlug(String slug) {
            return Optional.empty();
        }

        @Override
        public List<Organization> all() {
            return List.of();
        }
    };

    /** WEB-3: callers are anonymous until something replaces the resolver. */
    @Test
    void defaultsToAnonymousCallers() {
        runner.withBean(OrganizationResolver.class, () -> NONE).run(context -> {
            assertThat(context).hasSingleBean(FormatRequests.class);
            assertThat(context.getBean(PrincipalResolver.class).resolve(new MockHttpServletRequest()))
                    .isEqualTo(new Principal.Anonymous());
        });
    }

    @Test
    void usesTheAssemblysPrincipalResolver() {
        PrincipalResolver own = request -> new Principal.Anonymous();
        runner.withBean(OrganizationResolver.class, () -> NONE)
                .withBean(PrincipalResolver.class, () -> own)
                .run(context ->
                        assertThat(context.getBean(PrincipalResolver.class)).isSameAs(own));
    }

    /** WEB-6: organizations come from the path, through the directory, unless the service decides. */
    @Test
    void resolvesOrganizationsByPathFromTheDirectory() {
        runner.withBean(OrganizationDirectory.class, () -> NO_ORGANIZATIONS)
                .run(context -> assertThat(context.getBean(OrganizationResolver.class))
                        .isInstanceOf(PathOrganizationResolver.class));
        runner.withBean(OrganizationResolver.class, () -> NONE)
                .run(context ->
                        assertThat(context.getBean(OrganizationResolver.class)).isSameAs(NONE));
    }

    /** WEB-6: without a directory there's no way to find organizations, so Kiln doesn't start. */
    @Test
    void refusesToStartWithoutADirectory() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    /** WEB-5: the external URL is required, plain, and kept without a trailing slash. */
    @Test
    void requiresAPlainExternalUrl() {
        runner.withBean(OrganizationResolver.class, () -> NONE)
                .run(context -> assertThat(context.getBean(FormatRequests.class).baseUrl())
                        .isEqualTo("https://pkg.example.com"));
        for (String bad : new String[] {
            "",
            "ftp://pkg.example.com",
            "https://user@pkg.example.com",
            "https://pkg.example.com/?x=1",
            "https://pkg.example.com/#top",
            "/relative"
        }) {
            runner.withBean(OrganizationResolver.class, () -> NONE)
                    .withPropertyValues("kiln.web.external-url=" + bad)
                    .run(context -> assertThat(context).as(bad).hasFailed());
        }
    }

    /** WEB-4: Tomcat passes encoded slashes through to the formats, which parse names strictly. */
    @Test
    void letsEncodedSlashesThrough() {
        runner.withBean(OrganizationResolver.class, () -> NONE).run(context -> {
            Connector connector = new Connector();
            context.getBean(TomcatConnectorCustomizer.class).customize(connector);
            assertThat(connector.getEncodedSolidusHandling()).isEqualTo("passthrough");
        });
    }
}
