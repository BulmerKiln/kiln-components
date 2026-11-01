/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.format.cargo.CargoHarness;
import com.bulmerkiln.format.cargo.autoconfigure.CargoProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

/** FMT-CARGO-8: who may do what. */
class CargoAccessServiceTest {

    @TempDir
    static Path blobs;

    private static CargoHarness cargo;

    @BeforeAll
    static void start() throws Exception {
        cargo = new CargoHarness(blobs, "");
        cargo.publish("acme-core", "1.0.0", "private");
        cargo.publish("pub-open", "1.0.0", "public");
    }

    @AfterAll
    static void stop() {
        cargo.close();
    }

    @AfterEach
    void asTheWriter() {
        cargo.callAs(CargoHarness.WRITER);
    }

    @Test
    void asksAnonymousCallersForCredentials() throws Exception {
        cargo.callAs(CargoHarness.ANONYMOUS);

        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/ac/me/acme-core"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Cargo realm=\"Kiln\""));
        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/index/pu/b-/pub-open"))
                .andExpect(status().isOk());
        assertThat(cargo.publish("pub-open", "2.0.0", "x")).isEqualTo(401);
    }

    @Test
    void hidesPrivateCratesFromOtherMembers() throws Exception {
        cargo.callAs(CargoHarness.STRANGER);

        cargo.mvc()
                .perform(CargoHarness.at(HttpMethod.GET, "/cargo/api/v1/crates/acme-core/1.0.0/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].detail").exists());
    }

    @Test
    void refusesPublishingOutsideEveryNamespace() throws Exception {
        assertThat(cargo.publish("serde-extra", "1.0.0", "x")).isEqualTo(404);
        cargo.callAs(CargoHarness.ADMIN);
        assertThat(cargo.publish("serde-extra", "1.0.0", "x")).isEqualTo(403);
    }

    @Test
    void keepsTheOrganizationPrefixInLinks(@TempDir Path other) throws Exception {
        try (CargoHarness prefixed = new CargoHarness(other, "acme/")) {
            prefixed.mvc()
                    .perform(CargoHarness.at(HttpMethod.GET, "/cargo/acme/index/config.json"))
                    .andExpect(jsonPath("$.dl").value("http://localhost/cargo/acme/api/v1/crates"));
            prefixed.mvc()
                    .perform(CargoHarness.at(HttpMethod.GET, "/cargo/globex/index/config.json"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void defaultsItsLimit() {
        assertThat(new CargoProperties(null, null).maxCrateSizeOrDefault()).isEqualTo(10L * 1024 * 1024);
    }
}
