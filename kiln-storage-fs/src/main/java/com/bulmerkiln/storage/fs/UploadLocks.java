/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.storage.UploadId;
import com.bulmerkiln.storage.UploadNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes work on one upload session (STO-7). A file lock guards it across processes, but a JVM
 * can hold only one file lock per file, so threads in this process take an in-process lock first.
 */
final class UploadLocks {

    /** Work on an open upload, with both locks held. */
    @FunctionalInterface
    interface Work<T> {
        T run(FileChannel channel) throws IOException;
    }

    private final ReentrantLock[] locks = new ReentrantLock[64];

    UploadLocks() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * Opens the upload at {@code path} and runs {@code work} holding both locks.
     *
     * @throws UploadNotFoundException if the session doesn't exist
     */
    <T> T withUpload(UploadId upload, Path path, Work<T> work) throws IOException {
        ReentrantLock lock = locks[Math.floorMod(upload.value().hashCode(), locks.length)];
        lock.lock();
        try (FileChannel channel = open(path);
                FileLock _ = channel.lock()) {
            return work.run(channel);
        } finally {
            lock.unlock();
        }
    }

    private static FileChannel open(Path path) throws IOException {
        try {
            return FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (NoSuchFileException e) {
            throw new UploadNotFoundException();
        }
    }
}
