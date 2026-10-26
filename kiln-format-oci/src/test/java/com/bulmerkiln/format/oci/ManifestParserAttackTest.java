/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Manifests crafted so that Kiln and a client, or two parsers, could read them differently, or
 * that try to exhaust the parser.
 */
class ManifestParserAttackTest {

    private static final String DIGEST = "sha256:" + "c".repeat(64);
    private static final String CHILD =
            "{\"mediaType\":\"" + MediaTypes.OCI_MANIFEST + "\",\"digest\":\"" + DIGEST + "\",\"size\":1}";

    private final ManifestParser parser = new ManifestParser(ManifestParser.DEFAULT_MAX_SIZE);

    @Test
    void refusesDuplicateKeysHiddenByEscapes() {
        assertInvalid("{\"schemaVersion\":2,\"manifests\":[],\"manifest\\u0073\":[" + CHILD + "]}");
        assertInvalid("{\"schemaVersion\":2,\"manifests\":[" + CHILD + "],\"\\u006danifests\":[]}");
    }

    @Test
    void refusesAMediaTypeDuplicatedToConfuseNegotiation() {
        assertInvalid("{\"schemaVersion\":2,\"mediaType\":\"" + MediaTypes.OCI_INDEX + "\",\"mediaType\":\""
                + MediaTypes.OCI_MANIFEST + "\",\"manifests\":[]}");
    }

    @Test
    void ignoresCaseVariantKeysRatherThanTreatingThemAsTheRealOnes() {
        // Go's encoding/json matches keys case-insensitively; Kiln must not accept a manifest whose
        // meaning depends on that, so the real key is still required.
        assertInvalid("{\"SchemaVersion\":2,\"manifests\":[]}");
        assertInvalid("{\"schemaVersion\":2,\"Manifests\":[" + CHILD + "]}");
    }

    @Test
    void refusesUtf16AndUtf32() {
        String json = "{\"schemaVersion\":2,\"manifests\":[]}";
        for (var charset : new Charset[] {
            StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE, StandardCharsets.UTF_16, Charset.forName("UTF-32")
        }) {
            assertThatThrownBy(() -> parser.parse(json.getBytes(charset), MediaTypes.OCI_INDEX))
                    .as(charset.name())
                    .isInstanceOfSatisfying(
                            OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.MANIFEST_INVALID));
        }
    }

    @Test
    void refusesInvalidUtf8() {
        byte[] prefix = "{\"schemaVersion\":2,\"manifests\":[],\"x\":\"".getBytes(StandardCharsets.UTF_8);
        byte[][] sequences = {
            {(byte) 0xC0, (byte) 0xAF},
            {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
            {(byte) 0xFF},
            {(byte) 0xE2, (byte) 0x82}
        };
        for (byte[] bad : sequences) {
            byte[] raw = new byte[prefix.length + bad.length + 2];
            System.arraycopy(prefix, 0, raw, 0, prefix.length);
            System.arraycopy(bad, 0, raw, prefix.length, bad.length);
            raw[raw.length - 2] = '"';
            raw[raw.length - 1] = '}';
            assertThatThrownBy(() -> parser.parse(raw, MediaTypes.OCI_INDEX)).isInstanceOf(OciException.class);
        }
    }

    @Test
    void refusesALeadingByteOrderMark() {
        byte[] json = "\uFEFF{\"schemaVersion\":2,\"manifests\":[]}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parse(json, MediaTypes.OCI_INDEX)).isInstanceOf(OciException.class);
    }

    @Test
    void refusesDeeplyNestedJsonQuickly() {
        String deep = "{\"schemaVersion\":2,\"manifests\":[],\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertInvalid(deep));
    }

    @Test
    void handlesManyDescriptorsUpToTheSizeLimit() {
        StringBuilder json = new StringBuilder("{\"schemaVersion\":2,\"manifests\":[");
        for (int i = 0; i < 20_000; i++) {
            json.append(i == 0 ? "" : ",").append(CHILD);
        }
        json.append("]}");
        Manifest manifest = parser.parse(json.toString().getBytes(StandardCharsets.UTF_8), MediaTypes.OCI_INDEX);
        assertThat(manifest.manifests()).hasSize(20_000);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "9223372036854775808", // Long.MAX_VALUE + 1
                "-9223372036854775809",
                "1e3",
                "1.5",
                "1.0",
                "\"1\"",
                "true",
                "null",
                "[]",
                "{}",
                "0x10",
                "NaN",
                "Infinity"
            })
    void refusesDescriptorSizesThatAreNotPlainIntegers(String size) {
        assertInvalid("{\"schemaVersion\":2,\"manifests\":[{\"mediaType\":\"x\",\"digest\":\"" + DIGEST + "\",\"size\":"
                + size + "}]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0", "\"2\"", "true", "null", "[2]", "02", "-2", "2e0"})
    void refusesSchemaVersionsThatAreNotTheInteger2(String version) {
        assertInvalid("{\"schemaVersion\":" + version + ",\"manifests\":[]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "null", "{}", "\"x\"", "[\"x\"]", "[1]", "[[]]"})
    void refusesMalformedManifestLists(String manifests) {
        assertInvalid("{\"schemaVersion\":2,\"manifests\":" + manifests + "}");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"" + "SHA256:" + "c" + "\"",
                "\"sha256:CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC\"",
                "1",
                "null",
                "\"\"",
                "\"md5:d41d8cd98f00b204e9800998ecf8427e\""
            })
    void refusesBadDescriptorDigests(String digest) {
        assertInvalid(
                "{\"schemaVersion\":2,\"manifests\":[{\"mediaType\":\"x\",\"digest\":" + digest + ",\"size\":1}]}");
    }

    @Test
    void refusesSchema1EvenWhenOnlyTheVersionSaysSo() {
        assertThatThrownBy(() -> parser.parse(bytes("{\"schemaVersion\":1,\"manifests\":[]}"), MediaTypes.OCI_INDEX))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.UNSUPPORTED));
        assertThatThrownBy(() -> parser.parse(
                        bytes("{\"schemaVersion\":2}"), "application/vnd.docker.distribution.manifest.v1+json"))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.UNSUPPORTED));
    }

    @Test
    void checksTheSizeLimitBeforeParsing() {
        ManifestParser small = new ManifestParser(64);
        byte[] atLimit = bytes(("{\"schemaVersion\":2,\"manifests\":[]" + " ".repeat(64) + "}").substring(0, 63) + "}");
        assertThat(atLimit).hasSize(64);
        assertThat(small.parse(atLimit, MediaTypes.OCI_INDEX).manifests()).isEmpty();

        byte[] garbageOverLimit = new byte[65];
        assertThatThrownBy(() -> small.parse(garbageOverLimit, MediaTypes.OCI_INDEX))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.SIZE_INVALID));
    }

    @Test
    void errorsNeverEchoTheManifest() {
        String secret = "ghp_" + "x".repeat(36);
        assertThatThrownBy(() -> parser.parse(
                        bytes("{\"schemaVersion\":2,\"manifests\":[],\"x\":\"" + secret + "\"" + "}}"),
                        MediaTypes.OCI_INDEX))
                .isInstanceOf(OciException.class)
                .hasMessageNotContaining(secret)
                .hasNoCause();
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parser.parse(bytes(json), MediaTypes.OCI_INDEX))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.MANIFEST_INVALID));
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
