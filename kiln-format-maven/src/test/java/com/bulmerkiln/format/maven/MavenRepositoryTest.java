/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.format.maven.util.MavenChecksumUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

/** Deploying and resolving releases and snapshots through the {@code /maven/} routes. */
class MavenRepositoryTest {

    private static final String CORE = "org/acme/core/";

    @TempDir
    static Path blobs;

    private static MavenHarness maven;

    @BeforeAll
    static void start() throws Exception {
        maven = new MavenHarness(blobs, "");
        maven.deploy(CORE + "1.0/core-1.0.jar", "jar one");
        maven.deploy(CORE + "1.0/core-1.0.jar.sha1", MavenHarness.sha1("jar one"));
        maven.deploy(CORE + "1.0/core-1.0-sources.jar", "sources");
        maven.deploy(CORE + "1.0/core-1.0.pom", "<project/>");
        maven.deploy(CORE + "maven-metadata.xml", "<metadata>from the client</metadata>");
    }

    @AfterAll
    static void stop() {
        maven.close();
    }

    @AfterEach
    void asTheWriter() {
        maven.callAs(MavenHarness.WRITER);
    }

    /** FMT-MVN-10: files come back as deployed, with GET and HEAD. */
    @Test
    void servesDeployedFiles() throws Exception {
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0.jar"))
                .andExpect(status().isOk())
                .andExpect(content().string("jar one"));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.HEAD, "/maven/" + CORE + "1.0/core-1.0-sources.jar"))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", 7));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0.pom"))
                .andExpect(header().string("Content-Type", containsString("text/xml")));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0-javadoc.jar"))
                .andExpect(status().isNotFound());
    }

    /** FMT-MVN-12: every checksum is computed on the server; uploaded ones must match. */
    @Test
    void computesAndVerifiesChecksums() throws Exception {
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0.jar.sha1"))
                .andExpect(content().string(MavenHarness.sha1("jar one")));
        for (String suffix : new String[] {".md5", ".sha256", ".sha512"}) {
            maven.mvc()
                    .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0.jar" + suffix))
                    .andExpect(status().isOk());
        }
        assertThat(maven.deploy(CORE + "1.0/core-1.0.jar.sha1", "0".repeat(40))).isEqualTo(400);
        assertThat(maven.deploy(CORE + "1.0/core-1.0.jar.md5", "whatever")).isEqualTo(400);
        assertThat(maven.deploy(CORE + "9.0/core-9.0.jar.sha1", "0".repeat(40))).isEqualTo(201);
    }

    /** FMT-MVN-15: metadata comes from the database, never from what the client uploaded. */
    @Test
    void generatesArtifactMetadata() throws Exception {
        maven.deploy(CORE + "2.0/core-2.0.jar", "no pom yet");

        String xml = maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "maven-metadata.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("from the client"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(xml)
                .contains("<groupId>org.acme</groupId>", "<artifactId>core</artifactId>")
                .contains("<release>1.0</release>", "<version>1.0</version>")
                .doesNotContain("<version>2.0</version>");
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "maven-metadata.xml.sha1"))
                .andExpect(content()
                        .string(MavenChecksumUtils.checksum(
                                xml.getBytes(StandardCharsets.UTF_8), MavenChecksumAlgorithm.SHA1)));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/none/maven-metadata.xml"))
                .andExpect(status().isNotFound());
    }

    /**
     * FMT-MVN-17: a plugin's prefix comes from the group metadata its deploy uploads, for an
     * artifact already deployed; the group's metadata then lists it, and the plugin keeps its own.
     */
    @Test
    void servesPluginPrefixesFromGroupMetadata() throws Exception {
        String plugin = "org/acme/acme-maven-plugin/1.0/acme-maven-plugin-1.0";
        maven.deploy(plugin + ".jar", "plugin jar");
        maven.deploy(plugin + ".pom", "<project/>");
        String group = """
                <metadata><plugins>
                  <plugin>
                    <name>Acme Plugin</name><prefix>acme</prefix><artifactId>acme-maven-plugin</artifactId>
                  </plugin>
                  <plugin><prefix>ghost</prefix><artifactId>ghost-maven-plugin</artifactId></plugin>
                </plugins></metadata>
                """;
        assertThat(maven.deploy("org/acme/maven-metadata.xml", group)).isEqualTo(201);
        assertThat(maven.deploy("org/acme/maven-metadata.xml.sha1", MavenHarness.sha1(group)))
                .isEqualTo(201);

        String xml = maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/maven-metadata.xml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(xml)
                .contains(
                        "<name>Acme Plugin</name>",
                        "<prefix>acme</prefix>",
                        "<artifactId>acme-maven-plugin</artifactId>")
                .doesNotContain("ghost", "<versioning>");
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/acme-maven-plugin/maven-metadata.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<version>1.0</version>")));

        assertThat(maven.deploy(
                        "org/acme/maven-metadata.xml",
                        "<metadata><plugins><plugin><prefix>a b</prefix><artifactId>x</artifactId></plugin>"
                                + "</plugins></metadata>"))
                .isEqualTo(400);
        maven.callAs(MavenHarness.ANONYMOUS);
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/maven-metadata.xml"))
                .andExpect(status().isUnauthorized());
        assertThat(maven.deploy("org/acme/maven-metadata.xml", group)).isEqualTo(401);
    }

    /** FMT-MVN-11: a stored file never changes; the same bytes again are a no-op. */
    @Test
    void keepsReleasesImmutable() throws Exception {
        assertThat(maven.deploy(CORE + "1.0/core-1.0.jar", "jar one")).isEqualTo(201);
        assertThat(maven.deploy(CORE + "1.0/core-1.0.jar", "jar two")).isEqualTo(409);
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + CORE + "1.0/core-1.0.jar"))
                .andExpect(content().string("jar one"));
    }

    /** FMT-MVN-14: a -SNAPSHOT name resolves to the latest build, and its metadata says which. */
    @Test
    void resolvesSnapshots() throws Exception {
        String dir = "org/acme/snap/1.0-SNAPSHOT/";
        maven.deploy(dir + "snap-1.0-20261025.101500-1.jar", "build one");
        maven.deploy(dir + "snap-1.0-20261025.101500-1.pom", "<project/>");
        maven.deploy(dir + "snap-1.0-20261026.090000-2.jar", "build two");
        maven.deploy(dir + "snap-1.0-20261026.090000-2.pom", "<project/>");

        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + dir + "snap-1.0-SNAPSHOT.jar"))
                .andExpect(content().string("build two"));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + dir + "snap-1.0-20261025.101500-1.jar"))
                .andExpect(content().string("build one"));
        String xml = maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + dir + "maven-metadata.xml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(xml)
                .contains("<version>1.0-SNAPSHOT</version>")
                .contains("<timestamp>20261026.090000</timestamp>", "<buildNumber>2</buildNumber>")
                .contains("<value>1.0-20261026.090000-2</value>")
                .doesNotContain("101500-1</value>");
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/snap/maven-metadata.xml"))
                .andExpect(content().string(containsString("<latest>1.0-SNAPSHOT</latest>")))
                .andExpect(content().string(not(containsString("<release>"))));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/snap/2.0-SNAPSHOT/maven-metadata.xml"))
                .andExpect(status().isNotFound());
    }

    /** REG-4 through Maven: a blocked version is 403 with its reason, and leaves the metadata. */
    @Test
    void honoursBlocksAndTombstones() throws Exception {
        String dir = "org/acme/risky/";
        maven.deploy(dir + "1.0/risky-1.0.pom", "<project/>");
        maven.deploy(dir + "1.1/risky-1.1.pom", "<project/>");
        OrganizationContext.run(
                maven.acme(),
                () -> maven.database().run(() -> {
                    maven.registry()
                            .getBlocks()
                            .block(
                                    MavenFormatModule.FORMAT,
                                    "org.acme:risky",
                                    "1.1",
                                    "CVE-2026-0002",
                                    MavenHarness.ADMIN);
                    maven.registry().getBlocks().tombstone(MavenFormatModule.FORMAT, "org.acme:risky", "2.0");
                }));

        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + dir + "1.1/risky-1.1.pom"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("org.acme:risky 1.1 is blocked: CVE-2026-0002"));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/" + dir + "maven-metadata.xml"))
                .andExpect(content().string(not(containsString("<version>1.1</version>"))));
        assertThat(maven.deploy(dir + "2.0/risky-2.0.pom", "<project/>")).isEqualTo(409);
    }

    /** FMT-MVN-10: past the limit is 413; paths are parsed strictly and never echoed. */
    @Test
    void refusesOversizedFilesAndBadPaths() throws Exception {
        assertThat(maven.deploy(CORE + "3.0/core-3.0.jar", "x".repeat(65))).isEqualTo(413);
        assertThat(maven.deploy(CORE + "maven-metadata.xml", "x".repeat(1024 * 1024 + 1)))
                .isEqualTo(413);
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/core/1.0/../../evil.jar"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("evil"))));
        maven.mvc()
                .perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/core/1.0/other-1.0.jar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsPomDeploysAndDownloads() throws Exception {
        maven.events().clear();

        maven.deploy("org/acme/count/1.0/count-1.0.jar", "jar");
        maven.deploy("org/acme/count/1.0/count-1.0.pom", "<project/>");
        maven.mvc().perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/count/1.0/count-1.0.pom"));
        maven.mvc().perform(MavenHarness.at(HttpMethod.GET, "/maven/org/acme/count/1.0/count-1.0.jar"));

        assertThat(maven.events()).containsExactly("pushed org.acme:count 1.0", "pulled org.acme:count 1.0");
    }

    @Test
    void theSchemaIsOrganizationSafe() {
        assertThat(maven.database().instanceTransaction(SchemaLinter::problems)).isEmpty();
    }
}
