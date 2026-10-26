/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.InvalidDigestException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates pushed manifests and indexes. It never re-serializes them: the digest is computed over
 * the raw bytes and those bytes are what's stored.
 *
 * <p>Parsing is strict where leniency could let two parsers read one manifest differently:
 * anything but UTF-8 without a byte order mark, duplicate keys, trailing content and type coercion
 * are all rejected.
 */
public final class ManifestParser {

    /** Kiln's default limit (FMT-OCI-4). */
    public static final int DEFAULT_MAX_SIZE = 4 * 1024 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final int maxSize;

    public ManifestParser(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Validates a pushed manifest or index.
     *
     * @param contentType the request's {@code Content-Type}, which must match the manifest's own
     *     {@code mediaType} when it has one
     */
    public Manifest parse(byte[] raw, @Nullable String contentType) {
        Json json = read(raw);
        String mediaType = mediaType(json.mediaType(), contentType);
        requireSchemaVersion2(json, mediaType);
        List<Descriptor> blobs = blobs(json, mediaType);
        DescriptorJson subject = json.subject();
        return new Manifest(
                Digest.sha256(raw),
                mediaType,
                json.artifactType(),
                blobs,
                manifests(json, mediaType),
                subject == null ? null : descriptor(subject));
    }

    private Json read(byte[] raw) {
        if (raw.length > maxSize) {
            throw new OciException(OciErrorCode.SIZE_INVALID, "Manifest exceeds " + maxSize + " bytes");
        }
        Json json;
        try {
            json = MAPPER.readValue(decodeUtf8(raw), Json.class);
        } catch (JacksonException e) {
            throw invalid("Manifest is not valid JSON");
        }
        if (json == null) {
            throw invalid("Manifest is not a JSON object");
        }
        return json;
    }

    private static void requireSchemaVersion2(Json json, String mediaType) {
        if (MediaTypes.DOCKER_SCHEMA1.contains(mediaType) || Objects.equals(json.schemaVersion(), 1)) {
            throw new OciException(OciErrorCode.UNSUPPORTED, "Docker schema1 manifests are not supported");
        }
        if (!Objects.equals(json.schemaVersion(), 2)) {
            throw invalid("schemaVersion must be 2");
        }
    }

    /** The config and layers of an image manifest; none for an index. Refuses other media types. */
    private static List<Descriptor> blobs(Json json, String mediaType) {
        if (MediaTypes.INDEXES.contains(mediaType)) {
            if (json.manifests() == null || json.config() != null || json.layers() != null) {
                throw invalid("An index has manifests and no config or layers");
            }
            return List.of();
        }
        if (!MediaTypes.IMAGE_MANIFESTS.contains(mediaType)) {
            throw invalid("Unsupported manifest media type");
        }
        DescriptorJson config = json.config();
        if (config == null || json.manifests() != null) {
            throw invalid("An image manifest has a config and no manifests");
        }
        List<Descriptor> blobs = new ArrayList<>();
        blobs.add(descriptor(config));
        for (DescriptorJson layer : orEmpty(json.layers())) {
            blobs.add(descriptor(layer));
        }
        return List.copyOf(blobs);
    }

    /** The child manifests of an index; none for an image manifest. */
    private static List<Descriptor> manifests(Json json, String mediaType) {
        if (!MediaTypes.INDEXES.contains(mediaType)) {
            return List.of();
        }
        return orEmpty(json.manifests()).stream()
                .map(ManifestParser::descriptor)
                .toList();
    }

    /**
     * Jackson would detect UTF-16 and UTF-32 and skip a byte order mark, which Go clients don't
     * (RFC 8259, section 8.1: JSON exchanged between systems is UTF-8).
     */
    private static String decodeUtf8(byte[] raw) {
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            throw invalid("Manifest must not start with a byte order mark");
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException e) {
            throw invalid("Manifest is not valid UTF-8");
        }
    }

    private static String mediaType(@Nullable String declared, @Nullable String contentType) {
        String header = contentType == null ? null : stripParameters(contentType);
        if (declared != null) {
            if (header != null && !header.equals(declared)) {
                throw invalid("Content-Type does not match the manifest mediaType");
            }
            return declared;
        }
        if (header == null) {
            throw invalid("The manifest media type is unknown");
        }
        return header;
    }

    private static String stripParameters(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip();
    }

    private static Descriptor descriptor(@Nullable DescriptorJson json) {
        if (json == null) {
            throw invalid("A descriptor needs a mediaType, digest and size");
        }
        String mediaType = json.mediaType();
        String digest = json.digest();
        Long size = json.size();
        if (mediaType == null || digest == null || size == null) {
            throw invalid("A descriptor needs a mediaType, digest and size");
        }
        if (size < 0) {
            throw invalid("A descriptor size must not be negative");
        }
        try {
            return new Descriptor(mediaType, Digest.parse(digest), size, json.artifactType());
        } catch (InvalidDigestException e) {
            throw invalid("A descriptor has an invalid digest");
        }
    }

    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    private static OciException invalid(String message) {
        return new OciException(OciErrorCode.MANIFEST_INVALID, message);
    }

    /** The fields validation needs; everything else, such as annotations, is kept only in the raw bytes. */
    private record Json(
            @Nullable Integer schemaVersion,
            @Nullable String mediaType,
            @Nullable String artifactType,
            @Nullable DescriptorJson config,
            @Nullable List<DescriptorJson> layers,
            @Nullable List<DescriptorJson> manifests,
            @Nullable DescriptorJson subject) {}

    private record DescriptorJson(
            @Nullable String mediaType,
            @Nullable String digest,
            @Nullable Long size,
            @Nullable String artifactType) {}
}
