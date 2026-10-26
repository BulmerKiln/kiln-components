/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.digest.Digest;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ManifestParserTest {

    private static final String CONFIG = "sha256:" + "a".repeat(64);
    private static final String LAYER = "sha256:" + "b".repeat(64);
    private static final String CHILD = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    private final ManifestParser parser = new ManifestParser(ManifestParser.DEFAULT_MAX_SIZE);

    @Test
    void parsesAnImageManifest() {
        byte[] raw = bytes("""
                {"schemaVersion":2,"mediaType":"application/vnd.oci.image.manifest.v1+json",
                 "config":{"mediaType":"application/vnd.oci.image.config.v1+json","digest":"%s","size":7},
                 "layers":[{"mediaType":"application/vnd.oci.image.layer.v1.tar+gzip","digest":"%s","size":32}],
                 "annotations":{"org.opencontainers.image.source":"https://example.com"}}
                """.formatted(CONFIG, LAYER));

        Manifest manifest = parser.parse(raw, MediaTypes.OCI_MANIFEST);

        assertThat(manifest.digest()).isEqualTo(Digest.sha256(raw));
        assertThat(manifest.isIndex()).isFalse();
        assertThat(manifest.blobs()).extracting(d -> d.digest().toString()).containsExactly(CONFIG, LAYER);
        assertThat(manifest.manifests()).isEmpty();
        assertThat(manifest.subject()).isNull();
    }

    @Test
    void parsesAHelmChartAndAnArtifactWithASubject() {
        byte[] raw = bytes("""
                {"schemaVersion":2,"artifactType":"application/vnd.dev.cosign.artifact.sig.v1+json",
                 "config":{"mediaType":"application/vnd.oci.empty.v1+json","digest":"%s","size":2},
                 "layers":[],
                 "subject":{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","size":500}}
                """.formatted(CONFIG, CHILD));

        Manifest manifest = parser.parse(raw, MediaTypes.OCI_MANIFEST);

        assertThat(manifest.mediaType()).isEqualTo(MediaTypes.OCI_MANIFEST);
        assertThat(manifest.artifactType()).isEqualTo("application/vnd.dev.cosign.artifact.sig.v1+json");
        assertThat(manifest.subject()).isNotNull();
    }

    @Test
    void parsesAnIndex() {
        byte[] raw = bytes("""
                {"schemaVersion":2,"mediaType":"application/vnd.docker.distribution.manifest.list.v2+json",
                 "manifests":[{"mediaType":"application/vnd.docker.distribution.manifest.v2+json",
                   "digest":"%s","size":10,
                   "platform":{"architecture":"arm64","os":"linux"}}]}
                """.formatted(CHILD));

        Manifest manifest =
                parser.parse(raw, "application/vnd.docker.distribution.manifest.list.v2+json; charset=utf-8");

        assertThat(manifest.isIndex()).isTrue();
        assertThat(manifest.blobs()).isEmpty();
        assertThat(manifest.manifests())
                .singleElement()
                .extracting(d -> d.digest().toString())
                .isEqualTo(CHILD);
    }

    @Test
    void refusesDockerSchema1() {
        assertError(
                bytes("{\"schemaVersion\":1,\"name\":\"app\",\"tag\":\"latest\",\"fsLayers\":[]}"),
                "application/vnd.docker.distribution.manifest.v1+prettyjws",
                OciErrorCode.UNSUPPORTED);
    }

    @Test
    void refusesAMediaTypeThatDisagreesWithContentType() {
        byte[] raw =
                bytes("{\"schemaVersion\":2,\"mediaType\":\"%s\",\"manifests\":[]}".formatted(MediaTypes.OCI_INDEX));
        assertError(raw, MediaTypes.OCI_MANIFEST, OciErrorCode.MANIFEST_INVALID);
    }

    @Test
    void refusesOversizedManifests() {
        assertThatThrownBy(() -> new ManifestParser(16).parse(new byte[17], MediaTypes.OCI_MANIFEST))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.SIZE_INVALID));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "[]",
                "\"manifest\"",
                "{",
                "{}",
                // A duplicate key could make another parser see a different manifest.
                "{\"schemaVersion\":2,\"schemaVersion\":2,\"manifests\":[]}",
                "{\"schemaVersion\":2,\"manifests\":[]} {}",
                "{\"schemaVersion\":\"2\",\"manifests\":[]}",
                "{\"schemaVersion\":2.0,\"manifests\":[]}",
                "{\"schemaVersion\":2,\"manifests\":[{\"mediaType\":\"x\",\"digest\":\"sha256:nope\",\"size\":1}]}",
                "{\"schemaVersion\":2,\"manifests\":[{\"mediaType\":\"x\",\"digest\":\"" + CHILD + "\",\"size\":-1}]}",
                "{\"schemaVersion\":2,\"manifests\":[{\"mediaType\":\"x\",\"size\":1}]}",
                "{\"schemaVersion\":2,\"manifests\":[],\"config\":{\"mediaType\":\"x\",\"digest\":\"" + CHILD
                        + "\",\"size\":1}}",
                "{\"schemaVersion\":3,\"manifests\":[]}"
            })
    void refusesInvalidIndexes(String json) {
        assertError(bytes(json), MediaTypes.OCI_INDEX, OciErrorCode.MANIFEST_INVALID);
    }

    @Test
    void refusesUnknownMediaTypes() {
        assertError(bytes("{\"schemaVersion\":2,\"manifests\":[]}"), "application/json", OciErrorCode.MANIFEST_INVALID);
        assertError(bytes("{\"schemaVersion\":2,\"manifests\":[]}"), null, OciErrorCode.MANIFEST_INVALID);
    }

    @Test
    void parsesADockerV2Manifest() {
        byte[] raw = bytes("""
                {"schemaVersion":2,"mediaType":"application/vnd.docker.distribution.manifest.v2+json",
                 "config":{"mediaType":"application/vnd.docker.container.image.v1+json","digest":"%s","size":7},
                 "layers":[{"mediaType":"application/vnd.docker.image.rootfs.diff.tar.gzip","digest":"%s","size":32}]}
                """.formatted(CONFIG, LAYER));
        Manifest manifest = parser.parse(raw, MediaTypes.DOCKER_MANIFEST);
        assertThat(manifest.mediaType()).isEqualTo(MediaTypes.DOCKER_MANIFEST);
        assertThat(manifest.blobs()).hasSize(2);
    }

    @Test
    void takesTheMediaTypeFromContentTypeWhenTheManifestHasNone() {
        byte[] raw = bytes("{\"schemaVersion\":2,\"config\":{\"mediaType\":\"x\",\"digest\":\"%s\",\"size\":1}}"
                .formatted(CONFIG));
        assertThat(parser.parse(raw, MediaTypes.OCI_MANIFEST + "; charset=utf-8")
                        .mediaType())
                .isEqualTo(MediaTypes.OCI_MANIFEST);
        assertThat(parser.parse(raw, MediaTypes.OCI_MANIFEST).blobs()).hasSize(1);
    }

    @Test
    void takesTheMediaTypeFromTheManifestWhenThereIsNoContentType() {
        byte[] raw =
                bytes("{\"schemaVersion\":2,\"mediaType\":\"%s\",\"manifests\":[]}".formatted(MediaTypes.OCI_INDEX));
        assertThat(parser.parse(raw, null).isIndex()).isTrue();
    }

    @Test
    void keepsDescriptorArtifactTypesAndIndexSubjects() {
        byte[] raw = bytes("""
                {"schemaVersion":2,"mediaType":"application/vnd.oci.image.index.v1+json",
                 "artifactType":"application/example",
                 "manifests":[{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","size":10,
                   "artifactType":"application/sbom"}],
                 "subject":{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","size":3}}
                """.formatted(CHILD, CONFIG));
        Manifest manifest = parser.parse(raw, MediaTypes.OCI_INDEX);
        assertThat(manifest.artifactType()).isEqualTo("application/example");
        assertThat(manifest.manifests().getFirst().artifactType()).isEqualTo("application/sbom");
        assertThat(manifest.subject()).isNotNull();
        assertThat(manifest.subject().digest().toString()).isEqualTo(CONFIG);
    }

    @Test
    void theDigestCoversTheExactBytes() {
        byte[] compact = bytes("{\"schemaVersion\":2,\"manifests\":[]}");
        byte[] spaced = bytes("{ \"schemaVersion\": 2, \"manifests\": [] }\n");
        Manifest a = parser.parse(compact, MediaTypes.OCI_INDEX);
        Manifest b = parser.parse(spaced, MediaTypes.OCI_INDEX);
        assertThat(a.digest()).isEqualTo(Digest.sha256(compact)).isNotEqualTo(b.digest());
        assertThat(b.digest()).isEqualTo(Digest.sha256(spaced));
    }

    @Test
    void anImageManifestMayHaveNoLayers() {
        byte[] raw = bytes("{\"schemaVersion\":2,\"config\":{\"mediaType\":\"x\",\"digest\":\"%s\",\"size\":2}}"
                .formatted(CONFIG));
        assertThat(parser.parse(raw, MediaTypes.OCI_MANIFEST).blobs()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"schemaVersion\":2,\"layers\":[]}",
                "{\"schemaVersion\":2,\"config\":null}",
                "{\"schemaVersion\":2,\"config\":{\"mediaType\":\"x\",\"digest\":\"" + CHILD
                        + "\",\"size\":1},\"manifests\":[]}",
                "{\"schemaVersion\":2,\"config\":{\"mediaType\":\"x\",\"digest\":\"" + CHILD
                        + "\",\"size\":1},\"layers\":{}}",
                "{\"schemaVersion\":2,\"config\":{\"mediaType\":\"x\",\"digest\":\"" + CHILD
                        + "\",\"size\":1},\"layers\":[null]}",
                "{\"schemaVersion\":2,\"config\":{\"digest\":\"" + CHILD + "\",\"size\":1}}",
                "{\"config\":{\"mediaType\":\"x\",\"digest\":\"" + CHILD + "\",\"size\":1}}"
            })
    void refusesInvalidImageManifests(String json) {
        assertError(bytes(json), MediaTypes.OCI_MANIFEST, OciErrorCode.MANIFEST_INVALID);
    }

    @Test
    void anIndexMayNotCarryLayers() {
        assertError(
                bytes("{\"schemaVersion\":2,\"manifests\":[],\"layers\":[]}"),
                MediaTypes.OCI_INDEX,
                OciErrorCode.MANIFEST_INVALID);
    }

    @FuzzTest
    void parseOnlyFailsWithRegistryErrors(FuzzedDataProvider data) {
        String contentType = data.pickValue(
                new String[] {MediaTypes.OCI_MANIFEST, MediaTypes.OCI_INDEX, MediaTypes.DOCKER_MANIFEST});
        try {
            parser.parse(data.consumeRemainingAsBytes(), contentType);
        } catch (OciException expected) {
            // The only failure a pushed manifest may cause.
        }
    }

    private void assertError(byte[] raw, String contentType, OciErrorCode code) {
        assertThatThrownBy(() -> parser.parse(raw, contentType))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
