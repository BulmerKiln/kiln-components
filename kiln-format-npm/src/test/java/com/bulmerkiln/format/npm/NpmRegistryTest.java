/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** Publishing, installing, deprecating and tagging through the {@code /npm/} routes. */
class NpmRegistryTest {

    @TempDir
    static Path blobs;

    private static NpmHarness npm;

    @BeforeAll
    static void start() throws Exception {
        npm = new NpmHarness(blobs, "");
        npm.publish("@acme%2futil", NpmHarness.publishBody("@acme/util", "1.0.0", "tarball one"));
        npm.publish("@acme%2futil", NpmHarness.publishBody("@acme/util", "1.1.0", "tarball two"));
    }

    @AfterAll
    static void stop() {
        npm.close();
    }

    @AfterEach
    void asTheWriter() {
        npm.callAs(NpmHarness.WRITER);
    }

    /** FMT-NPM-10: the full packument, with tarball links to this registry; publishing moved latest. */
    @Test
    void servesTheFullPackument() throws Exception {
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("@acme/util"))
                .andExpect(jsonPath("$._rev").isString())
                .andExpect(jsonPath("$['dist-tags'].latest").value("1.1.0"))
                .andExpect(jsonPath("$.versions['1.1.0'].description").value("A package"))
                .andExpect(jsonPath("$.versions['1.0.0'].dist.tarball")
                        .value("http://localhost/npm/@acme%2futil/-/util-1.0.0.tgz"))
                .andExpect(jsonPath("$.time['1.0.0']").exists());
    }

    /** FMT-NPM-10, FMT-NPM-7: the abbreviated packument, by every accepted form of the name. */
    @Test
    void servesTheAbbreviatedPackument() throws Exception {
        for (String name : new String[] {"@acme%2futil", "@acme%2Futil", "@acme/util"}) {
            npm.mvc()
                    .perform(NpmHarness.at(HttpMethod.GET, "/npm/" + name)
                            .header("Accept", "application/vnd.npm.install-v1+json"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("application/vnd.npm.install-v1+json")))
                    .andExpect(jsonPath("$.versions['1.0.0'].dependencies['left-pad']")
                            .value("^1.0.0"))
                    .andExpect(jsonPath("$.versions['1.0.0'].description").doesNotExist());
        }
    }

    /** FMT-NPM-11: one version, and its tarball, byte for byte. */
    @Test
    void servesVersionsAndTarballs() throws Exception {
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil/1.1.0"))
                .andExpect(jsonPath("$.version").value("1.1.0"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil/-/util-1.1.0.tgz"))
                .andExpect(status().isOk())
                .andExpect(content().string("tarball two"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.HEAD, "/npm/@acme%2futil/-/util-1.1.0.tgz"))
                .andExpect(header().longValue("Content-Length", 11));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil/-/util-9.9.9.tgz"))
                .andExpect(status().isNotFound());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil/9.9.9"))
                .andExpect(status().isNotFound());
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fnothing")).andExpect(status().isNotFound());
    }

    /** FMT-NPM-13: versions never change; the same version again is a conflict. */
    @Test
    void refusesRepublishing() throws Exception {
        MvcResult again = npm.publish("@acme%2futil", NpmHarness.publishBody("@acme/util", "1.0.0", "different"));

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2futil/-/util-1.0.0.tgz"))
                .andExpect(content().string("tarball one"));
    }

    /** FMT-NPM-12, FMT-NPM-4, FMT-NPM-5: the tarball must match what the version says it is. */
    @Test
    void refusesTarballsThatDontMatchTheirIntegrity() throws Exception {
        String good = NpmHarness.publishBody("@acme/check", "1.0.0", "real bytes");
        String wrongSha512 = good.replaceFirst(
                "\"integrity\":\"sha512-[^\"]+\"", "\"integrity\":\"sha512-" + "A".repeat(86) + "==\"");
        String wrongShasum = good.replaceFirst("\"shasum\":\"[0-9a-f]+\"", "\"shasum\":\"" + "0".repeat(40) + "\"");
        String noSha512 = good.replaceFirst("\"integrity\":\"sha512-[^\"]+\"", "\"integrity\":\"sha1-abc\"");

        for (String body : new String[] {wrongSha512, wrongShasum, noSha512}) {
            MvcResult refused = npm.publish("@acme%2fcheck", body);
            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        }
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fcheck")).andExpect(status().isNotFound());
        assertThat(npm.publish("@acme%2fcheck", good).getResponse().getStatus()).isEqualTo(201);
    }

    /** FMT-NPM-12: one version, its tarball by its name, under the URL's package, within the limit. */
    @Test
    void refusesMalformedPublishes() throws Exception {
        String body = NpmHarness.publishBody("@acme/shape", "1.0.0", "x");

        assertThat(statusOf(npm.publish("@acme%2fother", body))).isEqualTo(400);
        assertThat(statusOf(npm.publish("@acme%2fshape", body.replace("shape-1.0.0.tgz", "evil.tgz"))))
                .isEqualTo(400);
        assertThat(statusOf(
                        npm.publish("@acme%2fshape", body.replace("\"version\":\"1.0.0\"", "\"version\":\"2.0.0\""))))
                .isEqualTo(400);
        assertThat(statusOf(npm.publish("@acme%2fshape", "{\"name\":\"@acme/shape\"")))
                .isEqualTo(400);
        assertThat(statusOf(npm.publish("@acme%2fshape", "[]"))).isEqualTo(400);
        assertThat(statusOf(npm.publish("@acme%2fshape", "{\"name\":\"@acme/shape\",\"versions\":[]}")))
                .isEqualTo(400);
        String twoTarballs = body.replace("\"_attachments\":{", "\"_attachments\":{\"a.tgz\":{\"data\":\"eA==\"},");
        assertThat(statusOf(npm.publish("@acme%2fshape", twoTarballs))).isEqualTo(400);
        String huge = NpmHarness.publishBody("@acme/shape", "1.0.0", "x".repeat(2000));
        assertThat(statusOf(npm.publish("@acme%2fshape", huge))).isEqualTo(413);
    }

    /**
     * FMT-NPM-9: publishing needs a namespace that owns the scope. A writer can't tell such a name
     * from one they may not see; an admin is told why.
     */
    @Test
    void refusesPublishingOutsideEveryNamespace() throws Exception {
        assertThat(statusOf(npm.publish("lodash", NpmHarness.publishBody("lodash", "1.0.0", "x"))))
                .isEqualTo(404);
        npm.callAs(NpmHarness.ADMIN);
        MvcResult unscoped = npm.publish("lodash", NpmHarness.publishBody("lodash", "1.0.0", "x"));
        MvcResult unowned = npm.publish("@nobody%2fx", NpmHarness.publishBody("@nobody/x", "1.0.0", "x"));

        assertThat(statusOf(unscoped)).isEqualTo(403);
        assertThat(unscoped.getResponse().getContentAsString()).contains("No namespace owns lodash");
        assertThat(statusOf(unowned)).isEqualTo(403);
    }

    /** FMT-NPM-9: an unscoped name belongs to the namespace claiming its longest prefix. */
    @Test
    void publishesUnscopedNamesANamespaceClaims() throws Exception {
        assertThat(statusOf(npm.publish("acme-ui", NpmHarness.publishBody("acme-ui", "1.0.0", "ui"))))
                .isEqualTo(201);
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/acme-ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['dist-tags'].latest").value("1.0.0"));
    }

    /** FMT-NPM-13: deprecating changes only the deprecation message. */
    @Test
    void deprecatesWithoutChangingAnythingElse() throws Exception {
        npm.publish("@acme%2fold", NpmHarness.publishBody("@acme/old", "1.0.0", "old"));
        String deprecate = """
                {"name":"@acme/old","versions":{"1.0.0":{"name":"@acme/old","version":"1.0.0",
                 "deprecated":"Use @acme/new","description":"Rewritten"}}}
                """;

        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/@acme%2fold")
                        .contentType("application/json")
                        .content(deprecate))
                .andExpect(status().isCreated());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fold/1.0.0"))
                .andExpect(jsonPath("$.deprecated").value("Use @acme/new"))
                .andExpect(jsonPath("$.description").value("A package"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/@acme%2fold")
                        .contentType("application/json")
                        .content(deprecate.replace("Use @acme/new", "")))
                .andExpect(status().isCreated());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fold/1.0.0"))
                .andExpect(jsonPath("$.deprecated").doesNotExist());
    }

    /** FMT-NPM-14: tags point at existing versions, and can be removed. */
    @Test
    void managesDistTags() throws Exception {
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/-/package/@acme%2futil/dist-tags/next")
                        .contentType("application/json")
                        .content("\"1.1.0\""))
                .andExpect(status().isCreated());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/-/package/@acme%2futil/dist-tags"))
                .andExpect(content().json("{\"latest\":\"1.1.0\",\"next\":\"1.1.0\"}"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/-/package/@acme%2futil/dist-tags/next"))
                .andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/-/package/@acme%2futil/dist-tags/next"))
                .andExpect(status().isNotFound());
        npm.publish("@acme%2funtagged", NpmHarness.publishBody("@acme/untagged", "1.0.0", "x"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/-/package/@acme%2funtagged/dist-tags/latest"))
                .andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/-/package/@acme%2funtagged/dist-tags"))
                .andExpect(content().json("{}"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/-/package/@acme%2futil/dist-tags/next")
                        .contentType("application/json")
                        .content("\"7.7.7\""))
                .andExpect(status().isNotFound());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/-/package/@acme%2futil/dist-tags/1.0.0")
                        .contentType("application/json")
                        .content("\"1.0.0\""))
                .andExpect(status().isBadRequest());
    }

    /**
     * FMT-NPM-18: the packument's last modification is the package's last change, so moving a
     * dist-tag shows, in the full and abbreviated packuments alike.
     */
    @Test
    void reportsTheLastChangeAsModified() throws Exception {
        npm.publish("@acme%2fdated", NpmHarness.publishBody("@acme/dated", "1.0.0", "x"));
        Instant published = Instant.parse(modified());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/-/package/@acme%2fdated/dist-tags/next")
                        .contentType("application/json")
                        .content("\"1.0.0\""))
                .andExpect(status().isCreated());

        assertThat(Instant.parse(modified())).isAfter(published);
        String abbreviated = npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fdated")
                        .header("Accept", "application/vnd.npm.install-v1+json"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(NpmPackumentBuilder.JSON
                        .readTree(abbreviated)
                        .get("modified")
                        .asString())
                .isEqualTo(modified());
    }

    private static String modified() throws Exception {
        String packument = npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fdated"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return NpmPackumentBuilder.JSON.readTree(packument).at("/time/modified").asString();
    }

    /**
     * FMT-NPM-17: a member who may delete unpublishes one version, then the package. Both numbers are
     * tombstoned, so neither comes back; a writer may not unpublish.
     */
    @Test
    void unpublishesVersionsAndPackages() throws Exception {
        npm.publish("@acme%2fgone", NpmHarness.publishBody("@acme/gone", "1.0.0", "one"));
        npm.publish("@acme%2fgone", NpmHarness.publishBody("@acme/gone", "1.1.0", "two"));
        String without = """
                {"_id":"@acme/gone","name":"@acme/gone","dist-tags":{"latest":"1.0.0"},
                 "versions":{"1.0.0":{"name":"@acme/gone","version":"1.0.0"}}}
                """;
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/@acme%2fgone/-rev/2-a")
                        .contentType("application/json")
                        .content(without))
                .andExpect(status().isForbidden());

        npm.callAs(NpmHarness.ADMIN);
        String rev = revision("@acme%2fgone");
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.PUT, "/npm/@acme%2fgone/-rev/" + rev)
                        .contentType("application/json")
                        .content(without))
                .andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fgone"))
                .andExpect(jsonPath("$.versions['1.0.0']").exists())
                .andExpect(jsonPath("$.versions['1.1.0']").doesNotExist())
                .andExpect(jsonPath("$['dist-tags'].latest").value("1.0.0"));
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/@acme%2fgone/-/gone-1.1.0.tgz/-rev/" + rev))
                .andExpect(status().isOk());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/@acme%2fgone/-/gone-1.0.0.tgz/-rev/" + rev))
                .andExpect(status().isConflict());
        assertThat(statusOf(npm.publish("@acme%2fgone", NpmHarness.publishBody("@acme/gone", "1.1.0", "again"))))
                .isEqualTo(409);

        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.DELETE, "/npm/@acme%2fgone/-rev/" + revision("@acme%2fgone")))
                .andExpect(status().isOk());
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fgone")).andExpect(status().isNotFound());
        assertThat(statusOf(npm.publish("@acme%2fgone", NpmHarness.publishBody("@acme/gone", "1.0.0", "again"))))
                .isEqualTo(409);
        assertThat(statusOf(npm.publish("@acme%2fgone", NpmHarness.publishBody("@acme/gone", "2.0.0", "new"))))
                .isEqualTo(201);
    }

    /** REG-4 through npm: blocked versions leave the packument and are 403; deleted ones stay gone. */
    @Test
    void honoursBlocksAndTombstones() throws Exception {
        npm.publish("@acme%2frisky", NpmHarness.publishBody("@acme/risky", "1.0.0", "fine"));
        npm.publish("@acme%2frisky", NpmHarness.publishBody("@acme/risky", "1.0.1", "bad"));
        OrganizationContext.run(
                npm.acme(),
                () -> npm.database().run(() -> {
                    npm.registry()
                            .getBlocks()
                            .block(NpmFormatModule.FORMAT, "@acme/risky", "1.0.1", "malware", NpmHarness.ADMIN);
                    npm.registry().getBlocks().tombstone(NpmFormatModule.FORMAT, "@acme/risky", "2.0.0");
                }));

        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2frisky"))
                .andExpect(jsonPath("$.versions['1.0.0']").exists())
                .andExpect(jsonPath("$.versions['1.0.1']").doesNotExist());
        npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2frisky/-/risky-1.0.1.tgz"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("@acme/risky 1.0.1 is blocked: malware"));
        assertThat(statusOf(npm.publish("@acme%2frisky", NpmHarness.publishBody("@acme/risky", "2.0.0", "again"))))
                .isEqualTo(409);
    }

    @Test
    void reportsPublishesAndDownloads() throws Exception {
        npm.events().clear();

        npm.publish("@acme%2fcount", NpmHarness.publishBody("@acme/count", "1.0.0", "x"));
        npm.mvc().perform(NpmHarness.at(HttpMethod.GET, "/npm/@acme%2fcount/-/count-1.0.0.tgz"));

        assertThat(npm.events()).containsExactly("pushed @acme/count 1.0.0", "pulled @acme/count 1.0.0");
    }

    @Test
    void theSchemaIsOrganizationSafe() {
        assertThat(npm.database().instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    private static String revision(String name) throws Exception {
        String packument = npm.mvc()
                .perform(NpmHarness.at(HttpMethod.GET, "/npm/" + name))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return NpmPackumentBuilder.JSON.readTree(packument).get("_rev").asString();
    }

    private static int statusOf(MvcResult result) {
        return result.getResponse().getStatus();
    }
}
