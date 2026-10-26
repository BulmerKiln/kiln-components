/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.ByteRange;
import com.bulmerkiln.storage.RangeNotSatisfiableException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** An open blob file, sent with zero-copy transfer so the bytes never pass through the heap. */
record FsBlobRead(FileChannel channel, long blobSize, long offset, long length) implements BlobRead {

    /** Opens the blob at {@code path}, or empty if it doesn't exist. */
    static Optional<BlobRead> open(Path path, @Nullable ByteRange range) throws IOException {
        FileChannel channel;
        try {
            channel = FileChannel.open(path, StandardOpenOption.READ);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
        try {
            return Optional.of(window(channel, range));
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /** The part of the blob to send: all of it, or the range, which must end within the blob (STO-9). */
    private static FsBlobRead window(FileChannel channel, @Nullable ByteRange range) throws IOException {
        long size = channel.size();
        if (range == null) {
            return new FsBlobRead(channel, size, 0, size);
        }
        if (range.last() >= size) {
            throw new RangeNotSatisfiableException(size);
        }
        return new FsBlobRead(channel, size, range.first(), range.length());
    }

    @Override
    public void transferTo(WritableByteChannel target) throws IOException {
        long position = offset;
        long end = offset + length;
        while (position < end) {
            long sent = channel.transferTo(position, end - position, target);
            if (sent <= 0 && channel.size() < end) {
                throw new IOException("Blob truncated while reading");
            }
            position += sent;
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
