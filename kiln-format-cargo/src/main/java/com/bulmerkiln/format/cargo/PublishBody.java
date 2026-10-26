/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.format.Reason;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code PUT /api/v1/crates/new} (Cargo "Registry web API", "Publish"): a 32-bit
 * little-endian length, the JSON metadata, another length, then the {@code .crate} file. Both
 * lengths are checked against limits and against the bytes actually present, and nothing may
 * follow the crate.
 */
public final class PublishBody {

    public static final int DEFAULT_MAX_METADATA = 1024 * 1024;

    private final byte[] metadataJson;
    private final byte[] crate;

    public PublishBody(byte[] metadataJson, byte[] crate) {
        this.metadataJson = metadataJson.clone();
        this.crate = crate.clone();
    }

    public byte[] metadataJson() {
        return metadataJson.clone();
    }

    public byte[] crate() {
        return crate.clone();
    }

    public static PublishBody parse(byte[] body, int maxMetadata, long maxCrate) {
        ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        byte[] metadata = chunk(buffer, maxMetadata, "metadata");
        byte[] crate = chunk(buffer, maxCrate, "crate");
        if (buffer.hasRemaining()) {
            throw new CargoException(Reason.INVALID, "Unexpected bytes after the crate");
        }
        return new PublishBody(metadata, crate);
    }

    private static byte[] chunk(ByteBuffer buffer, long max, String what) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new CargoException(Reason.INVALID, "The publish body is truncated before the " + what + " length");
        }
        long length = Integer.toUnsignedLong(buffer.getInt());
        if (length > max) {
            throw new CargoException(Reason.TOO_LARGE, "The " + what + " exceeds " + max + " bytes");
        }
        if (length > buffer.remaining()) {
            throw new CargoException(Reason.INVALID, "The publish body is shorter than its " + what + " length");
        }
        byte[] bytes = new byte[(int) length];
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PublishBody body
                && Arrays.equals(metadataJson, body.metadataJson)
                && Arrays.equals(crate, body.crate);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(metadataJson) + Arrays.hashCode(crate);
    }

    @Override
    public String toString() {
        return "PublishBody[metadata=" + metadataJson.length + " bytes, crate=" + crate.length + " bytes]";
    }
}
