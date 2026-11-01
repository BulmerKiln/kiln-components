/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.annotation.UntrustedInput;
import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.format.oci.dto.OciDescriptorRequest;
import com.bulmerkiln.format.oci.dto.OciManifestRequest;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
public final class OciManifestParser {

    /** Kiln's default limit (FMT-OCI-4). */
    public static final int DEFAULT_MAX_SIZE = 4 * 1024 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final int maxSize;

    public OciManifestParser(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Validates a pushed manifest or index.
     *
     * @param contentType the request's {@code Content-Type}, which must match the manifest's own
     *     {@code mediaType} when it has one
     */
    @UntrustedInput
    public OciManifest parse(byte[] raw, @Nullable String contentType) {
        OciManifestRequest json = read(raw);
        String mediaType = mediaType(json.getMediaType(), contentType);
        requireSchemaVersion2(json, mediaType);
        List<OciDescriptor> blobs = OciDescriptorReader.blobs(json, mediaType);
        OciDescriptorRequest subject = json.getSubject();
        return new OciManifest(
                Digest.sha256(raw),
                mediaType,
                json.getArtifactType(),
                blobs,
                OciDescriptorReader.manifests(json, mediaType),
                subject == null ? null : OciDescriptorReader.descriptor(subject));
    }

    private OciManifestRequest read(byte[] raw) {
        if (raw.length > maxSize) {
            throw new OciException(OciErrorCode.SIZE_INVALID, "Manifest exceeds " + maxSize + " bytes");
        }
        OciManifestRequest json;
        try {
            json = MAPPER.readValue(decodeUtf8(raw), OciManifestRequest.class);
        } catch (JacksonException e) {
            throw invalid("Manifest is not valid JSON");
        }
        if (json == null) {
            throw invalid("Manifest is not a JSON object");
        }
        return json;
    }

    private static void requireSchemaVersion2(OciManifestRequest json, String mediaType) {
        if (OciMediaTypes.DOCKER_SCHEMA1.contains(mediaType) || Objects.equals(json.getSchemaVersion(), 1)) {
            throw new OciException(OciErrorCode.UNSUPPORTED, "Docker schema1 manifests are not supported");
        }
        if (!Objects.equals(json.getSchemaVersion(), 2)) {
            throw invalid("schemaVersion must be 2");
        }
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

    static OciException invalid(String message) {
        return new OciException(OciErrorCode.MANIFEST_INVALID, message);
    }
}
