/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.core.digest.Digester;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.BlobTooLargeException;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;

/**
 * Where a tarball's decoded bytes go: appended to an upload session in chunks, and hashed for the
 * blob digest and for npm's integrity check on the way, so the tarball is never held whole
 * (FMT-NPM-12).
 */
public final class NpmTarballSink extends OutputStream {

    private static final int CHUNK = 1024 * 1024;

    private final BlobStore store;
    private final UploadId upload;
    private final long maxSize;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Digester digester = Digester.of(DigestAlgorithm.SHA256);
    private final NpmDistVerifier verifier = new NpmDistVerifier();
    private long offset;
    private @Nullable Digest digest;

    public NpmTarballSink(BlobStore store, UploadId upload, long maxSize) {
        this.store = store;
        this.upload = upload;
        this.maxSize = maxSize;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int off, int len) throws IOException {
        if (digest != null) {
            throw new IOException("The tarball is complete");
        }
        digester.update(bytes, off, len);
        verifier.update(bytes, off, len);
        buffer.write(bytes, off, len);
        if (buffer.size() >= CHUNK) {
            flush();
        }
    }

    @Override
    public void flush() throws IOException {
        if (buffer.size() == 0) {
            return;
        }
        try {
            offset = store.append(upload, offset, new ByteArrayInputStream(buffer.toByteArray()), maxSize);
        } catch (BlobTooLargeException e) {
            throw new NpmException(Reason.TOO_LARGE, "Tarballs are limited to " + maxSize + " bytes");
        }
        buffer.reset();
    }

    /** The SHA-256 of everything written, once the last write is done; further writes are refused. */
    public Digest digest() throws IOException {
        Digest done = digest;
        if (done == null) {
            flush();
            done = digester.digest();
            digest = done;
        }
        return done;
    }

    public NpmDistVerifier verifier() {
        return verifier;
    }
}
