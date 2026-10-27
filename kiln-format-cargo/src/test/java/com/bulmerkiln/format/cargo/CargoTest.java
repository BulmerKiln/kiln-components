/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.dto.CargoPublishRequest;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CargoTest {

    @ParameterizedTest
    @CsvSource({
        "a, 1/a",
        "ab, 2/ab",
        "abc, 3/a/abc",
        "serde, se/rd/serde",
        "Serde_JSON, se/rd/serde_json",
        "tokio-util, to/ki/tokio-util"
    })
    void computesSparseIndexPaths(String name, String path) {
        assertThat(new CargoCrateName(name).indexPath()).isEqualTo(path);
    }

    @Test
    void namesCollideIgnoringCaseAndSeparators() {
        assertThat(new CargoCrateName("Serde_JSON").canonical())
                .isEqualTo(new CargoCrateName("serde-json").canonical());
        assertThat(new CargoCrateName("serde_json").canonical())
                .isNotEqualTo(new CargoCrateName("serdejson").canonical());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "1serde",
                "_serde",
                "-serde",
                "serde json",
                "serde.json",
                "serde/json",
                "../serde",
                "s\u00e9rde",
                "\u0455erde",
                "serde\u0000",
                "con",
                "NUL",
                "Com1",
                "lpt9",
                "std",
                "core",
                "alloc",
                "proc_macro",
                "Proc-Macro",
                "test"
            })
    void refusesInvalidAndReservedNames(String name) {
        assertThatThrownBy(() -> new CargoCrateName(name))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(400));
    }

    @Test
    void limitsNameLength() {
        assertThat(new CargoCrateName("a".repeat(64))).isNotNull();
        assertThatThrownBy(() -> new CargoCrateName("a".repeat(65))).isInstanceOf(CargoException.class);
    }

    @Test
    void parsesAPublishBody() {
        byte[] json = "{\"name\":\"kiln\",\"vers\":\"0.1.0\"}".getBytes(StandardCharsets.UTF_8);
        byte[] crate = {1, 2, 3, 4};
        CargoPublishRequest body =
                CargoPublishRequest.parse(body(json, crate), CargoPublishRequest.DEFAULT_MAX_METADATA, 100);

        assertThat(body)
                .isEqualTo(new CargoPublishRequest(json, crate))
                .hasSameHashCodeAs(new CargoPublishRequest(json, crate));
        assertThat(body.toString()).doesNotContain("kiln");
    }

    @Test
    void acceptsEmptyParts() {
        assertThat(CargoPublishRequest.parse(body(new byte[0], new byte[0]), 10, 10)
                        .crate())
                .isEmpty();
    }

    @Test
    void refusesTruncatedBodies() {
        byte[] full = body(new byte[] {'{', '}'}, new byte[] {9, 9});
        for (int length = 0; length < full.length; length++) {
            byte[] truncated = Arrays.copyOf(full, length);
            assertThatThrownBy(() -> CargoPublishRequest.parse(truncated, 100, 100))
                    .as("length " + length)
                    .isInstanceOf(CargoException.class);
        }
    }

    @Test
    void refusesTrailingBytes() {
        byte[] body = body(new byte[] {'{', '}'}, new byte[] {9});
        byte[] trailing = Arrays.copyOf(body, body.length + 1);
        assertThatThrownBy(() -> CargoPublishRequest.parse(trailing, 100, 100)).hasMessageContaining("after the crate");
    }

    @Test
    void refusesLengthsOverTheLimitsBeforeAllocating() {
        assertThatThrownBy(() -> CargoPublishRequest.parse(body(new byte[11], new byte[0]), 10, 10))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(413));
        assertThatThrownBy(() -> CargoPublishRequest.parse(body(new byte[1], new byte[11]), 10, 10))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(413));
    }

    @Test
    void readsLengthsAsUnsigned() {
        // 0xFFFFFFFF is 4 GiB, not -1, which a signed read could turn into an empty chunk.
        byte[] huge = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .putInt(-1)
                .array();
        assertThatThrownBy(() -> CargoPublishRequest.parse(huge, 10, Long.MAX_VALUE))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(400))
                .hasMessageContaining("shorter");
        assertThatThrownBy(() -> CargoPublishRequest.parse(huge, 10, 10))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(413));
        byte[] hugeMetadata =
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).array();
        assertThatThrownBy(() -> CargoPublishRequest.parse(hugeMetadata, Integer.MAX_VALUE, 10))
                .isInstanceOfSatisfying(
                        CargoException.class, e -> assertThat(e.status()).isEqualTo(413));
    }

    @FuzzTest
    void parsingOnlyFailsWithCargoErrors(FuzzedDataProvider data) {
        try {
            CargoPublishRequest.parse(data.consumeRemainingAsBytes(), 1 << 16, 1 << 20);
        } catch (CargoException expected) {
            // The only failure a body may cause.
        }
        try {
            new CargoCrateName(data.consumeRemainingAsString());
        } catch (CargoException expected) {
            // Same for names.
        }
    }

    private static byte[] body(byte[] json, byte[] crate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(json.length)
                .array());
        out.writeBytes(json);
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(crate.length)
                .array());
        out.writeBytes(crate);
        return out.toByteArray();
    }
}
