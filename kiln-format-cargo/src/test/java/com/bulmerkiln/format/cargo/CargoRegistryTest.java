/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.registry.store.BlockStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Publishing, indexing, downloading and yanking through the {@code /cargo/} routes. */
class CargoRegistryTest {

    @TempDir
    static Path blobs;

    private static CargoHarness cargo;

    @BeforeAll
    static void start() throws Exception {
        cargo = new CargoHarness(blobs, "");
        cargo.publish("acme-core", "1.0.0", "crate one");
        cargo.publish("acme-core", "1.1.0", "crate two");
    }

    @AfterAll
    static void stop() {
        cargo.close();
    }

    @AfterEach
    void asTheWriter() {
        cargo.callAs(CargoHarness.WRITER);
    }

    /** FMT-CARGO-11: config.json says where downloads and the API are, and that auth is required. */
    @Test
    void servesTheConfig() throws Exception {
        cargo.callAs(CargoHarness.ANONYMOUS);

        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/config.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dl").value("http://localhost/cargo/api/v1/crates"))
                .andExpect(jsonPath("$.api").value("http://localhost/cargo"))
                .andExpect(jsonPath("$['auth-required']").value(true));
    }

    /** FMT-CARGO-9, FMT-CARGO-11: one line per version, as the sparse index has them. */
    @Test
    void servesTheIndexFile() throws Exception {
        MvcResult result = cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-core"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("Last-Modified"))
                .andReturn();
        String[] lines = result.getResponse().getContentAsString().split("\n");
        JsonNode first = CargoIndexEntry.JSON.readTree(lines[0]);

        assertThat(lines).hasSize(2);
        assertThat(first.get("vers").asString()).isEqualTo("1.0.0");
        assertThat(first.get("cksum").asString())
                .isEqualTo(Digest.sha256("crate one".getBytes(StandardCharsets.UTF_8))
                        .encoded());
        assertThat(first.get("deps").get(0).get("req").asString()).isEqualTo("^1.0");
        assertThat(first.get("deps").get(1).get("name").asString()).isEqualTo("rt");
        assertThat(first.get("deps").get(1).get("package").asString()).isEqualTo("tokio");
        assertThat(first.get("deps").get(1).get("kind").asString()).isEqualTo("dev");
        assertThat(first.get("yanked").asBoolean()).isFalse();
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-core")
                        .header("If-None-Match", result.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/xx/me/acme-core"))
                .andExpect(status().isNotFound());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-none"))
                .andExpect(status().isNotFound());
    }

    /** FMT-CARGO-12: downloads; a yanked version stays in the index and downloadable. */
    @Test
    void downloadsAndYanks() throws Exception {
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-core/1.1.0/download"))
                .andExpect(status().isOk())
                .andExpect(content().string("crate two"));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.DELETE, "/cargo/api/v1/crates/acme-core/1.1.0/yank"))
                .andExpect(status().isOk());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-core"))
                .andExpect(content().string(containsString("\"vers\":\"1.1.0\"")))
                .andExpect(content().string(containsString("\"yanked\":true")));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-core/1.1.0/download"))
                .andExpect(status().isOk());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.PUT, "/cargo/api/v1/crates/acme-core/1.1.0/unyank"))
                .andExpect(status().isOk());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-core"))
                .andExpect(content().string(not(containsString("\"yanked\":true"))));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.DELETE, "/cargo/api/v1/crates/acme-core/9.0.0/yank"))
                .andExpect(status().isNotFound());
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.PUT, "/cargo/api/v1/crates/acme-core/1.1.0/yank"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** FMT-CARGO-10, FMT-CARGO-3: versions never change, and a name can't be taken under another spelling. */
    @Test
    void refusesRepublishingAndLookalikes() throws Exception {
        assertThat(cargo.publish("acme-core", "1.0.0", "again")).isEqualTo(409);
        assertThat(cargo.publish("acme_Core", "2.0.0", "lookalike")).isEqualTo(409);
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-core/1.0.0/download"))
                .andExpect(content().string("crate one"));
    }

    /** FMT-CARGO-5, FMT-CARGO-10: malformed bodies, metadata and sizes are refused. */
    @Test
    void refusesMalformedPublishes() throws Exception {
        byte[] good = CargoHarness.publishBody(CargoHarness.metadata("acme-shape", "1.0.0"), "c");
        byte[] truncated = Arrays.copyOf(good, good.length - 1);
        assertThat(put(truncated)).isEqualTo(400);
        assertThat(put(CargoHarness.publishBody("not json", "c"))).isEqualTo(400);
        assertThat(put(CargoHarness.publishBody("{\"name\":\"acme-shape\"}", "c")))
                .isEqualTo(400);
        assertThat(put(CargoHarness.publishBody(CargoHarness.metadata("acme-shape", "1.0"), "c")))
                .isEqualTo(400);
        assertThat(put(CargoHarness.publishBody(CargoHarness.metadata("std", "1.0.0"), "c")))
                .isEqualTo(400);
        assertThat(put(CargoHarness.publishBody(CargoHarness.metadata("acme-shape", "1.0.0"), "x".repeat(65))))
                .isEqualTo(413);
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.POST, "/cargo/api/v1/crates/new"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** REG-4 through Cargo: a blocked version leaves the index and is 403; a deleted one stays gone. */
    @Test
    void honoursBlocksAndTombstones() throws Exception {
        cargo.publish("acme-risky", "1.0.0", "fine");
        cargo.publish("acme-risky", "1.0.1", "bad");
        OrganizationContext.run(
                cargo.acme(),
                () -> cargo.database().run(tx -> {
                    BlockStore.block(tx, CargoFormatModule.FORMAT, "acme-risky", "1.0.1", "malware");
                    BlockStore.tombstone(tx, CargoFormatModule.FORMAT, "acme-risky", "2.0.0");
                }));

        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-risky"))
                .andExpect(content().string(not(containsString("1.0.1"))));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-risky/1.0.1/download"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].detail").value("acme-risky 1.0.1 is blocked: malware"));
        assertThat(cargo.publish("acme-risky", "2.0.0", "again")).isEqualTo(409);
    }

    /** FMT-CARGO-11: Cargo commands Kiln doesn't serve get guidance in Cargo's error format. */
    @Test
    void guidesUnsupportedCommands() throws Exception {
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-core/owners"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].detail").value(containsString("cargo owner isn't supported")));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates?q=x"))
                .andExpect(jsonPath("$.errors[0].detail").value(containsString("cargo search")));
    }

    @Test
    void reportsPublishesAndDownloads() throws Exception {
        cargo.events().clear();

        cargo.publish("acme-count", "1.0.0", "c");
        cargo.mvc().perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-count/1.0.0/download"));

        assertThat(cargo.events()).containsExactly("pushed acme-count 1.0.0", "pulled acme-count 1.0.0");
    }

    @Test
    void theSchemaIsOrganizationSafe() {
        assertThat(cargo.database().instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    private static int put(byte[] body) throws Exception {
        return cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.PUT, "/cargo/api/v1/crates/new")
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
