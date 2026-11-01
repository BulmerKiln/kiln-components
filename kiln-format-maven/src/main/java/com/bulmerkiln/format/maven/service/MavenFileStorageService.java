/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.format.maven.domain.MavenDeployedFile;
import com.bulmerkiln.format.maven.util.MavenChecksumUtils;
import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.BlobTooLargeException;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.domain.BlobInfo;
import com.bulmerkiln.storage.domain.UploadId;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Maven files in the organization's blob store: stored as they stream in, hashed for every checksum
 * Maven asks for on the way, and read back (FMT-MVN-10, FMT-MVN-12). A checksum request answers from
 * the checksums kept with the blob, so it never reads the file again.
 */
public final class MavenFileStorageService {

    private final Database database;
    private final BlobRecordService blobs;
    private final StorageRouter storage;
    private final long maxFileSize;

    public MavenFileStorageService(
            Database database, BlobRecordService blobs, StorageRouter storage, long maxFileSize) {
        this.database = database;
        this.blobs = blobs;
        this.storage = storage;
        this.maxFileSize = maxFileSize;
    }

    /** Stores a file's bytes, hashing them for every checksum on the way, refusing one past the limit. */
    public MavenDeployedFile store(InputStream body) throws IOException {
        Map<MavenChecksumAlgorithm, MessageDigest> hashes = MavenChecksumUtils.hashes();
        BlobStore store = store();
        UploadId upload = store.createUpload();
        try {
            InputStream hashing = body;
            for (MessageDigest hash : hashes.values()) {
                hashing = new DigestInputStream(hashing, hash);
            }
            store.append(upload, 0, hashing, maxFileSize);
            Digest digest =
                    new Digest(DigestAlgorithm.SHA256, MavenChecksumUtils.hex(hashes, MavenChecksumAlgorithm.SHA256));
            BlobInfo blob = store.commit(upload, digest);
            return new MavenDeployedFile(
                    blob,
                    new BlobChecksums(
                            MavenChecksumUtils.hex(hashes, MavenChecksumAlgorithm.MD5),
                            MavenChecksumUtils.hex(hashes, MavenChecksumAlgorithm.SHA1),
                            MavenChecksumUtils.hex(hashes, MavenChecksumAlgorithm.SHA256),
                            MavenChecksumUtils.hex(hashes, MavenChecksumAlgorithm.SHA512)));
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

    /**
     * A stored file's checksum, as taken when it was deployed. A blob recorded without one, as
     * another format may have recorded it, is hashed once and the checksum kept.
     */
    public String checksum(PackageFile stored, MavenChecksumAlgorithm algorithm) throws IOException {
        Digest digest = stored.getDigest();
        Optional<String> recorded = database.transaction(() -> blobs.checksums(digest))
                .map(checksums -> MavenChecksumUtils.pick(checksums, algorithm));
        if (recorded.isPresent()) {
            return recorded.get();
        }
        String computed;
        try (BlobRead read = open(digest)) {
            computed = MavenChecksumUtils.checksum(read, algorithm);
        }
        database.run(() -> blobs.record(digest, stored.getSize(), MavenChecksumUtils.only(algorithm, computed)));
        return computed;
    }

    private BlobStore store() {
        return storage.current();
    }
}
