/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.store;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.BlobTooLargeException;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Files in the organization's blob store: stored as they stream in, hashed on the way, and
 * checksummed on the way out (FMT-MVN-10, FMT-MVN-12).
 */
public final class MavenFileStore {

    private final StorageRouter storage;
    private final long maxFileSize;

    public MavenFileStore(StorageRouter storage, long maxFileSize) {
        this.storage = storage;
        this.maxFileSize = maxFileSize;
    }

    /** Stores a file's bytes and returns the blob, refusing one past the limit. */
    public BlobInfo store(InputStream body) throws IOException {
        BlobStore store = store();
        UploadId upload = store.createUpload();
        try {
            DigestInputStream hashing = new DigestInputStream(body, messageDigest("SHA-256"));
            store.append(upload, 0, hashing, maxFileSize);
            Digest digest = new Digest(
                    DigestAlgorithm.SHA256,
                    HexFormat.of().formatHex(hashing.getMessageDigest().digest()));
            return store.commit(upload, digest);
        } catch (BlobTooLargeException e) {
            store.abortUpload(upload);
            throw new RefusedException(Refusal.of(Reason.TOO_LARGE, "Files are limited to " + maxFileSize + " bytes"));
        } catch (IOException | RuntimeException e) {
            store.abortUpload(upload);
            throw e;
        }
    }

    public BlobRead open(Digest digest) throws IOException {
        return store().read(digest, null)
                .orElseThrow(() -> new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found")));
    }

    /** A stored file's checksum, as lowercase hex, computed from its bytes. */
    public String checksum(Digest digest, MavenChecksumAlgorithm algorithm) throws IOException {
        MessageDigest hash = messageDigest(algorithm.jcaName());
        try (BlobRead read = open(digest)) {
            read.transferTo(Channels.newChannel(new DigestOutputStream(OutputStream.nullOutputStream(), hash)));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    public static String checksum(byte[] bytes, MavenChecksumAlgorithm algorithm) {
        return HexFormat.of().formatHex(messageDigest(algorithm.jcaName()).digest(bytes));
    }

    private BlobStore store() {
        return storage.store(OrganizationContext.current());
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides " + algorithm, e);
        }
    }
}
