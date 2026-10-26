/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.storage.BlobTooLargeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Writes upload chunks within the upload's size limit. */
final class Chunks {

    private static final int BUFFER_SIZE = 64 * 1024;

    private Chunks() {}

    /**
     * Writes {@code data} at {@code position} and returns the new end.
     *
     * @throws BlobTooLargeException as soon as the upload would grow past {@code maxSize}
     */
    static long write(InputStream data, FileChannel channel, long position, long maxSize) throws IOException {
        long end = position;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = data.read(buffer)) != -1) {
            if (read > maxSize - end) {
                throw new BlobTooLargeException(maxSize);
            }
            ByteBuffer chunk = ByteBuffer.wrap(buffer, 0, read);
            while (chunk.hasRemaining()) {
                end += channel.write(chunk, end);
            }
        }
        return end;
    }
}
