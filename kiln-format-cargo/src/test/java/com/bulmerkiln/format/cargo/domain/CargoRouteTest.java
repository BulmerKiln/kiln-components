/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FMT-CARGO-7: routes below {@code /cargo/}. */
class CargoRouteTest {

    @Test
    void readsEveryRoute() {
        CargoCrateName serde = new CargoCrateName("serde");
        assertThat(CargoRoute.parse("index/config.json")).isEqualTo(new CargoRoute.Config());
        assertThat(CargoRoute.parse("index/se/rd/serde")).isEqualTo(new CargoRoute.Index(serde));
        assertThat(CargoRoute.parse("index/1/a")).isEqualTo(new CargoRoute.Index(new CargoCrateName("a")));
        assertThat(CargoRoute.parse("api/v1/crates/new")).isEqualTo(new CargoRoute.Publish());
        assertThat(CargoRoute.parse("api/v1/crates/serde/1.0.0/download"))
                .isEqualTo(new CargoRoute.Download(serde, "1.0.0"));
        assertThat(CargoRoute.parse("api/v1/crates/serde/1.0.0/yank"))
                .isEqualTo(new CargoRoute.Yank(serde, "1.0.0", true));
        assertThat(CargoRoute.parse("api/v1/crates/serde/1.0.0/unyank"))
                .isEqualTo(new CargoRoute.Yank(serde, "1.0.0", false));
        assertThat(CargoRoute.parse("api/v1/crates/serde/owners")).isEqualTo(new CargoRoute.Unsupported("owner"));
        assertThat(CargoRoute.parse("api/v1/crates")).isEqualTo(new CargoRoute.Unsupported("search"));
    }

    @ParameterizedTest
    @CsvSource({
        "index/serde",
        "index/se/rd/Serde",
        "index/../config.json",
        "api/v1/crates/serde/1.0/download",
        "api/v1/crates/serde/1.0.0/delete",
        "api/v1/crates/serde",
        "api/v2/crates/new",
        "elsewhere"
    })
    void refusesWhatIsNoValidRoute(String path) {
        assertThatThrownBy(() -> CargoRoute.parse(path)).isInstanceOf(RefusedException.class);
    }

    @Test
    void refusesOverlongPaths() {
        assertThatThrownBy(() -> CargoRoute.parse("a".repeat(600))).isInstanceOf(CargoException.class);
    }

    @FuzzTest(maxDuration = "10s")
    void neverThrowsAnythingButARefusal(String path) {
        if (path == null) {
            return;
        }
        try {
            CargoRoute.parse(path);
        } catch (RefusedException e) {
            assertThat(e.status()).isBetween(400, 499);
        }
    }

    /** Whatever version a URL carries, validation fails only with CargoException. */
    @FuzzTest
    void versionFailsOnlyWithCargoException(String value) {
        if (value == null) {
            return;
        }
        try {
            CargoRoute.version(value);
        } catch (CargoException expected) {
            // The only failure a crafted version may cause.
        }
    }
}
