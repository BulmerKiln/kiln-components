/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.dto;

import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * What {@code npm publish} and {@code npm deprecate} send: the package document, with any tarball
 * base64-encoded in {@code _attachments}. It's read as a stream, and each attachment's bytes are
 * decoded straight into the sink the caller provides (FMT-NPM-12).
 */
public record NpmPublishRequest(
        @Nullable String name, ObjectNode versions, ObjectNode distTags, List<String> attachments) {

    /**
     * Reads a body of at most {@code maxLength} characters, decoding each attachment into a sink
     * from {@code sinks}. Anything that isn't a JSON object of the expected shape is {@code 400}.
     */
    public static NpmPublishRequest read(InputStream body, long maxLength, Supplier<OutputStream> sinks)
            throws IOException {
        JsonMapper json = JsonMapper.builder(JsonFactory.builder()
                        .streamReadConstraints(StreamReadConstraints.builder()
                                .maxDocumentLength(maxLength)
                                .maxNestingDepth(64)
                                .build())
                        .build())
                // Each subtree is read mid-stream, with the rest of the document still to come.
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        try (JsonParser parser = json.createParser(body)) {
            return read(json, parser, sinks);
        } catch (JacksonException e) {
            throw invalid("The publish body is not valid JSON");
        }
    }

    private static NpmPublishRequest read(JsonMapper json, JsonParser parser, Supplier<OutputStream> sinks)
            throws IOException {
        expect(parser.nextToken(), JsonToken.START_OBJECT);
        String name = null;
        ObjectNode versions = json.createObjectNode();
        ObjectNode distTags = json.createObjectNode();
        List<String> attachments = new ArrayList<>();
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (field) {
                case "name" -> name = value == JsonToken.VALUE_STRING ? parser.getString() : null;
                case "versions" -> versions = object(json.readTree(parser));
                case "dist-tags" -> distTags = object(json.readTree(parser));
                case "_attachments" -> readAttachments(parser, value, sinks, attachments);
                default -> parser.skipChildren();
            }
        }
        return new NpmPublishRequest(name, versions, distTags, List.copyOf(attachments));
    }

    /** Each attachment is {@code {"content_type", "data", "length"}}; only {@code data} is read. */
    private static void readAttachments(
            JsonParser parser, JsonToken start, Supplier<OutputStream> sinks, List<String> attachments)
            throws IOException {
        expect(start, JsonToken.START_OBJECT);
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            attachments.add(parser.currentName());
            expect(parser.nextToken(), JsonToken.START_OBJECT);
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                JsonToken value = parser.nextToken();
                if ("data".equals(parser.currentName()) && value == JsonToken.VALUE_STRING) {
                    try (OutputStream sink = sinks.get()) {
                        parser.readBinaryValue(sink);
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
    }

    private static ObjectNode object(JsonNode node) {
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw invalid("versions, dist-tags and _attachments must be objects");
    }

    private static void expect(@Nullable JsonToken actual, JsonToken expected) {
        if (actual != expected) {
            throw invalid("The publish body has an unexpected shape");
        }
    }

    private static NpmException invalid(String message) {
        return new NpmException(Reason.INVALID, message);
    }
}
