/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.Digester;
import com.bulmerkiln.storage.BlobInfo;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.ByteRange;
import com.bulmerkiln.storage.DigestMismatchException;
import com.bulmerkiln.storage.UploadId;
import com.bulmerkiln.storage.UploadNotFoundException;
import com.bulmerkiln.storage.UploadOffsetException;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BlobStore} on a local or shared filesystem, laid out per organization (STO-FS-1):
 *
 * <pre>
 * &lt;root&gt;/&lt;org-id&gt;/blobs/sha256/&lt;ab&gt;/&lt;hex&gt;   immutable, content-addressed
 * &lt;root&gt;/&lt;org-id&gt;/_uploads/&lt;session-uuid&gt;      in-progress uploads
 * </pre>
 *
 * <p>Committing re-hashes the upload, so a session survives restarts and can continue on another
 * replica. Finished uploads move into place with an atomic rename, which the constructor checks the
 * filesystem supports.
 */
public final class FsBlobStore implements BlobStore {

    private final FsLayout layout;
    private final UploadLocks locks = new UploadLocks();

    /**
     * Opens the store at {@code root}, creating it if needed.
     *
     * @throws java.nio.file.AtomicMoveNotSupportedException if the filesystem can't rename
     *     atomically, which several replicas sharing it would rely on (STO-FS-2)
     */
    public FsBlobStore(Path root) throws IOException {
        this.layout = new FsLayout(root);
    }

    @Override
    public UploadId createUpload() throws IOException {
        UploadId upload = UploadId.random();
        Files.createDirectories(layout.uploadsDirectory());
        Files.createFile(layout.uploadPath(upload));
        return upload;
    }

    @Override
    public long uploadSize(UploadId upload) throws IOException {
        try {
            return Files.size(layout.uploadPath(upload));
        } catch (NoSuchFileException e) {
            throw new UploadNotFoundException();
        }
    }

    @Override
    public long append(UploadId upload, long offset, InputStream data, long maxSize) throws IOException {
        return locks.withUpload(upload, layout.uploadPath(upload), channel -> {
            long size = channel.size();
            if (offset != size) {
                throw new UploadOffsetException(size, offset);
            }
            // A chunk is applied entirely or not at all (STO-3).
            try {
                long end = Chunks.write(data, channel, size, maxSize);
                channel.force(false);
                return end;
            } catch (IOException | RuntimeException e) {
                channel.truncate(size);
                throw e;
            }
        });
    }

    @Override
    public BlobInfo commit(UploadId upload, Digest expected) throws IOException {
        Path source = layout.uploadPath(upload);
        return locks.withUpload(upload, source, channel -> moveIntoPlace(channel, source, expected));
    }

    @Override
    public void abortUpload(UploadId upload) throws IOException {
        Files.deleteIfExists(layout.uploadPath(upload));
    }

    @Override
    public Optional<BlobInfo> stat(Digest digest) throws IOException {
        try {
            return Optional.of(new BlobInfo(digest, Files.size(layout.blobPath(digest))));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BlobRead> read(Digest digest, @Nullable ByteRange range) throws IOException {
        return FsBlobRead.open(layout.blobPath(digest), range);
    }

    @Override
    public boolean delete(Digest digest) throws IOException {
        return Files.deleteIfExists(layout.blobPath(digest));
    }

    @Override
    @MustBeClosed
    public Stream<Digest> list() throws IOException {
        return layout.list();
    }

    /** Verifies the upload (STO-5), then renames it into place (STO-6). */
    private BlobInfo moveIntoPlace(FileChannel channel, Path source, Digest expected) throws IOException {
        Digester digester = Digester.of(expected.algorithm());
        digester.update(Channels.newInputStream(channel.position(0)));
        Digest actual = digester.digest();
        if (!actual.equals(expected)) {
            Files.deleteIfExists(source);
            throw new DigestMismatchException(expected, actual);
        }
        channel.force(true);
        long size = channel.size();
        Path directory = Files.createDirectories(layout.blobDirectory(expected));
        try {
            // Replaces an existing blob on POSIX, which is safe: it's the same content.
            Files.move(source, directory.resolve(expected.encoded()), StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(source);
        }
        FsLayout.syncDirectory(directory);
        return new BlobInfo(expected, size);
    }
}
