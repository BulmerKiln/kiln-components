/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.Digester;
import com.bulmerkiln.core.org.OrgContext;
import com.bulmerkiln.core.org.OrgId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A {@link BlobStore} in memory, for tests of the layers above storage. It passes the same
 * {@link BlobStoreContract} as the real backends. Stores built on one {@link State} see the same
 * blobs, which stands in for a restart.
 */
public final class InMemoryBlobStore implements BlobStore {

    /** The stored bytes, shared by every store built on it. */
    public static final class State {
        private final Map<Key<Digest>, byte[]> blobs = new ConcurrentHashMap<>();
        private final Map<Key<UploadId>, Upload> uploads = new ConcurrentHashMap<>();
    }

    record Key<T>(OrgId org, T id) {}

    static final class Upload {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    }

    private final State state;

    public InMemoryBlobStore() {
        this(new State());
    }

    public InMemoryBlobStore(State state) {
        this.state = state;
    }

    @Override
    public UploadId createUpload() {
        UploadId upload = UploadId.random();
        state.uploads.put(key(upload), new Upload());
        return upload;
    }

    @Override
    public long uploadSize(UploadId upload) {
        Upload session = session(upload);
        synchronized (session) {
            return session.bytes.size();
        }
    }

    @Override
    public long append(UploadId upload, long offset, InputStream data, long maxSize) throws IOException {
        Upload session = session(upload);
        synchronized (session) {
            long size = session.bytes.size();
            if (offset != size) {
                throw new UploadOffsetException(size, offset);
            }
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = data.read(buffer)) != -1) {
                if (read > maxSize - size - chunk.size()) {
                    throw new BlobTooLargeException(maxSize);
                }
                chunk.write(buffer, 0, read);
            }
            if (!state.uploads.containsKey(key(upload))) {
                throw new UploadNotFoundException();
            }
            chunk.writeTo(session.bytes);
            return session.bytes.size();
        }
    }

    @Override
    public BlobInfo commit(UploadId upload, Digest expected) {
        Upload session = session(upload);
        synchronized (session) {
            if (state.uploads.remove(key(upload)) == null) {
                throw new UploadNotFoundException();
            }
            byte[] content = session.bytes.toByteArray();
            Digester digester = Digester.of(expected.algorithm());
            digester.update(content, 0, content.length);
            Digest actual = digester.digest();
            if (!actual.equals(expected)) {
                throw new DigestMismatchException(expected, actual);
            }
            state.blobs.putIfAbsent(key(expected), content);
            return new BlobInfo(expected, content.length);
        }
    }

    @Override
    public void abortUpload(UploadId upload) {
        state.uploads.remove(key(upload));
    }

    @Override
    public Optional<BlobInfo> stat(Digest digest) {
        return Optional.ofNullable(state.blobs.get(key(digest))).map(bytes -> new BlobInfo(digest, bytes.length));
    }

    @Override
    public Optional<BlobRead> read(Digest digest, ByteRange range) {
        byte[] content = state.blobs.get(key(digest));
        if (content == null) {
            return Optional.empty();
        }
        if (range != null && range.last() >= content.length) {
            throw new RangeNotSatisfiableException(content.length);
        }
        long offset = range == null ? 0 : range.first();
        long length = range == null ? content.length : range.length();
        return Optional.of(new Read(content, offset, length));
    }

    @Override
    public boolean delete(Digest digest) {
        return state.blobs.remove(key(digest)) != null;
    }

    @Override
    public Stream<Digest> list() {
        OrgId org = OrgContext.current().id();
        List<Digest> digests = state.blobs.keySet().stream()
                .filter(k -> k.org().equals(org))
                .map(Key::id)
                .toList();
        return digests.stream();
    }

    private Upload session(UploadId upload) {
        Upload session = state.uploads.get(key(upload));
        if (session == null) {
            throw new UploadNotFoundException();
        }
        return session;
    }

    private static <T> Key<T> key(T id) {
        return new Key<>(OrgContext.current().id(), id);
    }

    private static final class Read implements BlobRead {

        private final byte[] content;
        private final long offset;
        private final long length;

        Read(byte[] content, long offset, long length) {
            this.content = content;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public long blobSize() {
            return content.length;
        }

        @Override
        public long offset() {
            return offset;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public void transferTo(WritableByteChannel target) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(content, (int) offset, (int) length);
            while (buffer.hasRemaining()) {
                target.write(buffer);
            }
        }

        @Override
        public void close() {}
    }
}
