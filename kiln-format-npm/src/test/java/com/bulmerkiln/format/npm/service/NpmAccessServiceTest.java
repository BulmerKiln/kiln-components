/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.format.npm.NpmHarness;
import com.bulmerkiln.permissions.domain.Principal;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

/** FMT-NPM-9, FMT-NPM-15, FMT-NPM-16: who may do what, the health checks, and what isn't supported. */
class NpmAccessServiceTest {

    @TempDir
    static Path blobs;

    private static NpmHarness npm;

    @BeforeAll
    static void start() throws Exception {
        npm = new NpmHarness(blobs, "");
        npm.publish("@acme%2fprivate", NpmHarness.publishBody("@acme/private", "1.0.0", "secret"));
        npm.publish("@pub%2fopen", NpmHarness.publishBody("@pub/open", "1.0.0", "open"));
    }

    @AfterAll
    static void stop() {
        npm.close();
    }

    @AfterEach
    void asTheWriter() {
        npm.callAs(NpmHarness.WRITER);
    }

    @Test
    void asksAnonymousCallersToSignIn() throws Exception {
        npm.callAs(NpmHarness.ANONYMOUS);

        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fprivate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"http://localhost/npm/\""));
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@pub%2fopen")).andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@pub%2fopen/-/open-1.0.0.tgz"))
                .andExpect(status().isOk());
        assertThat(npm.publish("@pub%2fopen", NpmHarness.publishBody("@pub/open", "2.0.0", "x"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void hidesPrivatePackagesFromOtherMembers() throws Exception {
        npm.callAs(NpmHarness.STRANGER);

        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fprivate")).andExpect(status().isNotFound());
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fmissing")).andExpect(status().isNotFound());
    }

    /** FMT-NPM-16: ping and whoami, which tooling uses as health checks. */
    @Test
    void answersPingAndWhoami() throws Exception {
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/-/ping")).andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/-/whoami"))
                .andExpect(jsonPath("$.username")
                        .value(((Principal.Member) NpmHarness.WRITER)
                                .id()
                                .value()
                                .toString()));
        npm.callAs(NpmHarness.ANONYMOUS);
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/-/whoami")).andExpect(status().isUnauthorized());
    }

    /** FMT-NPM-16: npm commands Kiln doesn't serve are a 404 with guidance, never a 5xx. */
    @Test
    void guidesUnsupportedCommands() throws Exception {
        for (String path : new String[] {
            "/npm/-/v1/search?text=x", "/npm/-/user/org.couchdb.user:me", "/npm/-/npm/v1/security/advisories/bulk"
        }) {
            npm.mvc()
                    .perform(NpmHarness.at(HttpMethod.GET, path))
                    .andExpect(status().isNotFound())
                    .andExpect(header().exists("npm-notice"))
                    .andExpect(jsonPath("$.error").exists());
        }
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/@acme%2fprivate/-rev/1-abc"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                                "npm-notice",
                                "npm unpublish isn't supported by Kiln; use the Kiln web app or the kiln CLI"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.POST, "/npm/@acme%2fprivate"))
                .andExpect(status().isMethodNotAllowed());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/@acme%2fprivate/-/private-1.0.0.tgz"))
                .andExpect(status().isMethodNotAllowed());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/-/package/@acme%2fprivate/access"))
                .andExpect(status().isNotFound());
    }

    /** With organizations addressed by path, tarball links keep the prefix. */
    @Test
    void keepsTheOrganizationPrefixInLinks(@TempDir Path other) throws Exception {
        try (NpmHarness prefixed = new NpmHarness(other, "acme/")) {
            prefixed.publish("acme/@acme%2fp", NpmHarness.publishBody("@acme/p", "1.0.0", "p"));
            prefixed.mvc()
                    .perform(NpmHarness.at(HttpMethod.GET, "/npm/acme/@acme%2fp"))
                    .andExpect(jsonPath("$.versions['1.0.0'].dist.tarball")
                            .value("http://localhost/npm/acme/@acme%2fp/-/p-1.0.0.tgz"));
            prefixed.mvc()
                    .perform(NpmHarness.at(HttpMethod.GET, "/npm/globex/@acme%2fp"))
                    .andExpect(status().isNotFound());
        }
    }
}
