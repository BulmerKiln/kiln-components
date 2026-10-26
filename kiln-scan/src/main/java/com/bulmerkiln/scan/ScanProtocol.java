/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes protocol messages. The version is bumped for any change a reader of the
 * previous version would misread; adding an optional field doesn't need one, since unknown fields
 * are ignored.
 */
public final class ScanProtocol {

    public static final int VERSION = 1;

    /** Results above this are refused before parsing; the largest real reports are far smaller. */
    public static final int MAX_MESSAGE_BYTES = 64 * 1024 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private ScanProtocol() {}

    public static byte[] write(Object message) {
        return MAPPER.writeValueAsBytes(message);
    }

    public static ScanJob readJob(byte[] json) {
        return read(json, ScanJob.class);
    }

    public static ScanResult readResult(byte[] json) {
        return read(json, ScanResult.class);
    }

    static void requireVersion(int protocol) {
        if (protocol != VERSION) {
            throw new UnsupportedProtocolException(protocol);
        }
    }

    private static <T> T read(byte[] json, Class<T> type) {
        if (json.length > MAX_MESSAGE_BYTES) {
            throw new InvalidMessageException("Message exceeds " + MAX_MESSAGE_BYTES + " bytes");
        }
        try {
            T message = MAPPER.readValue(json, type);
            if (message == null) {
                throw new InvalidMessageException("Empty message");
            }
            return message;
        } catch (JacksonException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsupportedProtocolException unsupported) {
                throw unsupported;
            }
            throw new InvalidMessageException("Invalid " + type.getSimpleName());
        }
    }

    /** A message that doesn't parse or validate; its content is never echoed. */
    public static class InvalidMessageException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public InvalidMessageException(String message) {
            super(message);
        }
    }

    /** A message from a newer or older worker that this Kiln doesn't speak. */
    public static class UnsupportedProtocolException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public UnsupportedProtocolException(int version) {
            super("Unsupported scan protocol version " + version + "; this Kiln speaks " + VERSION);
        }
    }
}
