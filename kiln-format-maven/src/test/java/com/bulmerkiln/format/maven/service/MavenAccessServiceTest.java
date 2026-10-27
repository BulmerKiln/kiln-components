/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.format.maven.MavenHarness;
import com.bulmerkiln.format.maven.autoconfigure.MavenProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

/** FMT-MVN-9, FMT-MVN-13: who may do what, and the challenge Maven needs to send credentials. */
class MavenAccessServiceTest {

    @TempDir
    static Path blobs;

    private static MavenHarness maven;

    @BeforeAll
    static void start() throws Exception {
        maven = new MavenHarness(blobs, "");
        maven.deploy("org/acme/core/1.0/core-1.0.pom", "<project/>");
        maven.deploy("org/pub/open/1.0/open-1.0.pom", "<project/>");
    }

    @AfterAll
    static void stop() {
        maven.close();
    }

    @AfterEach
    void asTheWriter() {
        maven.callAs(MavenHarness.WRITER);
    }

    /** FMT-MVN-13: Maven sends credentials only after a Basic challenge. */
    @Test
    void challengesAnonymousCallersWithBasic() throws Exception {
        maven.callAs(MavenHarness.ANONYMOUS);

        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/core/1.0/core-1.0.pom"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Kiln\""));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/pub/open/1.0/open-1.0.pom"))
                .andExpect(status().isOk());
        assertThat(maven.deploy("org/pub/open/2.0/open-2.0.pom", "<project/>")).isEqualTo(401);
    }

    @Test
    void hidesPrivateArtifactsFromOtherMembers() throws Exception {
        maven.callAs(MavenHarness.STRANGER);

        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/core/1.0/core-1.0.pom"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesDeploysOutsideEveryNamespace() throws Exception {
        maven.callAs(MavenHarness.ADMIN);

        assertThat(maven.deploy("com/other/x/1.0/x-1.0.pom", "<project/>")).isEqualTo(403);
        maven.callAs(MavenHarness.WRITER);
        assertThat(maven.deploy("com/other/x/1.0/x-1.0.pom", "<project/>")).isEqualTo(404);
    }

    /** Deletes go through Kiln, never Maven. */
    @Test
    void refusesOtherMethods() throws Exception {
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.DELETE, "/maven/org/acme/core/1.0/core-1.0.pom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string("Method not allowed"));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.POST, "/maven/org/acme/core/maven-metadata.xml"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void keepsTheOrganizationPrefixOutOfStorage(@TempDir Path other) throws Exception {
        try (MavenHarness prefixed = new MavenHarness(other, "acme/")) {
            assertThat(prefixed.deploy("acme/org/acme/p/1.0/p-1.0.pom", "<project/>"))
                    .isEqualTo(201);
            prefixed.mvc()
                    .perform(MavenHarness.at(HttpMethod.GET, "/maven/acme/org/acme/p/1.0/p-1.0.pom"))
                    .andExpect(status().isOk());
            prefixed.mvc()
                    .perform(MavenHarness.at(HttpMethod.GET, "/maven/globex/org/acme/p/1.0/p-1.0.pom"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void defaultsItsLimit() {
        assertThat(new MavenProperties(null, null).maxFileSizeOrDefault()).isEqualTo(2L * 1024 * 1024 * 1024);
    }
}
