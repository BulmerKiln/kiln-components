/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.org.Org;
import com.bulmerkiln.storage.BlobInfo;
import com.bulmerkiln.storage.BlobStore;
import com.bulmerkiln.storage.BlobStoreContract;
import com.bulmerkiln.storage.UploadId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The filesystem store: the shared contract, plus its on-disk layout and root handling. */
class FsBlobStoreTest extends BlobStoreContract {

    @TempDir
    Path root;

    @Override
    protected BlobStore newStore() throws IOException {
        return new FsBlobStore(root);
    }

    @Override
    protected void assertNoUploadsLeft(Org org) throws IOException {
        try (Stream<Path> uploads = Files.list(root.resolve(org.id().toString()).resolve("_uploads"))) {
            assertThat(uploads).isEmpty();
        }
    }

    @Test
    void laysBlobsOutByOrganizationAndDigest() throws Exception {
        BlobInfo info = in(acme, () -> push(HELLO));
        assertThat(root.resolve(acme.id().toString())
                        .resolve("blobs/sha256")
                        .resolve(info.digest().encoded().substring(0, 2))
                        .resolve(info.digest().encoded()))
                .exists();
    }

    @Test
    void listSkipsFilesThatAreNotBlobs() throws Exception {
        in(acme, () -> push(HELLO));
        Path blobs = root.resolve(acme.id().toString()).resolve("blobs");
        Files.createDirectories(blobs.resolve("sha256/zz"));
        Files.writeString(blobs.resolve("sha256/zz/not-a-digest"), "x");
        Files.writeString(blobs.resolve("sha256/stray"), "x");
        Files.createDirectories(blobs.resolve("md5/d4"));
        Files.writeString(blobs.resolve("md5/d4/d41d8cd98f00b204e9800998ecf8427e"), "x");

        try (Stream<Digest> listed = in(acme, store::list)) {
            assertThat(listed).containsExactly(HELLO_DIGEST);
        }
    }

    @Test
    void createsItsRootAndLeavesNoProbeFiles() throws Exception {
        Path nested = root.resolve("a/b/c");
        new FsBlobStore(nested);
        assertThat(nested).isDirectory();
        try (Stream<Path> files = Files.list(nested)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void normalizesItsRoot() throws Exception {
        Path sneaky = root.resolve("x/../store");
        FsBlobStore normalized = new FsBlobStore(sneaky);
        in(acme, () -> {
            UploadId upload = normalized.createUpload();
            normalized.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
            return normalized.commit(upload, HELLO_DIGEST);
        });
        assertThat(root.resolve("store").resolve(acme.id().toString())).isDirectory();
        assertThat(root.resolve("x")).doesNotExist();
    }

    @Test
    void refusesARootThatIsAFile() throws Exception {
        Path file = Files.writeString(root.resolve("file"), "x");
        assertThatThrownBy(() -> new FsBlobStore(file)).isInstanceOf(IOException.class);
    }
}
