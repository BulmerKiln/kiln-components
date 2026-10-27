/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.core.digest.Digester;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link BlobStore} must have. Each backend's test extends this and provides
 * {@link #newStore()}; backend-specific tests sit alongside in the subclass.
 */
public abstract class BlobStoreContract {

    protected static final byte[] HELLO = "hello, kiln".getBytes(StandardCharsets.UTF_8);
    protected static final Digest HELLO_DIGEST = Digest.sha256(HELLO);

    protected final Organization acme = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    protected final Organization globex = new Organization(new OrganizationId(UUID.randomUUID()), "globex");

    protected BlobStore store;

    /**
     * Opens a store on this test's backing storage. A second call must open the same storage, as a
     * restarted replica would.
     */
    protected abstract BlobStore newStore() throws IOException;

    /** Checks the backend kept nothing of finished uploads. Backends that can look override it. */
    protected void assertNoUploadsLeft(Organization organization) throws IOException {}

    @BeforeEach
    void openStore() throws IOException {
        store = newStore();
    }

    @Test
    void storesAMonolithicUpload() throws Exception {
        BlobInfo info = in(acme, () -> push(HELLO));

        assertThat(info).isEqualTo(new BlobInfo(HELLO_DIGEST, HELLO.length));
        assertThat(in(acme, () -> store.stat(HELLO_DIGEST))).contains(info);
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, null))).isEqualTo(HELLO);
    }

    @Test
    void storesAChunkedUpload() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            assertThat(store.append(upload, 0, stream("hello, "), 100)).isEqualTo(7);
            assertThat(store.append(upload, 7, stream("kiln"), 100)).isEqualTo(11);
            assertThat(store.uploadSize(upload)).isEqualTo(11);
            return store.commit(upload, HELLO_DIGEST);
        });
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, null))).isEqualTo(HELLO);
    }

    @Test
    void refusesGapsAndOverlaps() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, stream("hello"), 100);
            assertThatThrownBy(() -> store.append(upload, 3, stream("lo"), 100))
                    .isInstanceOfSatisfying(
                            UploadOffsetException.class,
                            e -> assertThat(e.expected()).isEqualTo(5));
            assertThatThrownBy(() -> store.append(upload, 9, stream("x"), 100))
                    .isInstanceOf(UploadOffsetException.class);
            assertThat(store.uploadSize(upload)).isEqualTo(5);
            return null;
        });
    }

    @Test
    void leavesTheUploadUnchangedWhenAChunkIsTooLarge() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, stream("hello"), 10);
            assertThatThrownBy(() -> store.append(upload, 5, stream(", kiln!"), 10))
                    .isInstanceOfSatisfying(
                            BlobTooLargeException.class,
                            e -> assertThat(e.limit()).isEqualTo(10));
            assertThat(store.uploadSize(upload)).isEqualTo(5);
            return null;
        });
    }

    @Test
    void leavesTheUploadUnchangedWhenTheClientDisconnects() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, stream("hello"), 1 << 20);
            // 100 KB arrive, then the connection drops.
            InputStream broken =
                    new SequenceInputStream(new ByteArrayInputStream(new byte[100_000]), new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("connection reset");
                        }

                        @Override
                        public int read(byte[] buffer, int offset, int length) throws IOException {
                            throw new IOException("connection reset");
                        }
                    });
            assertThatThrownBy(() -> store.append(upload, 5, broken, 1 << 20)).hasMessage("connection reset");
            assertThat(store.uploadSize(upload)).isEqualTo(5);
            return null;
        });
    }

    @Test
    void deletesTheUploadWhenTheDigestDoesNotMatch() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, stream("not hello"), 100);
            assertThatThrownBy(() -> store.commit(upload, HELLO_DIGEST)).isInstanceOf(DigestMismatchException.class);
            assertThatThrownBy(() -> store.uploadSize(upload)).isInstanceOf(UploadNotFoundException.class);
            assertThat(store.stat(HELLO_DIGEST)).isEmpty();
            return null;
        });
    }

    @Test
    void committingExistingContentIsIdempotent() throws Exception {
        in(acme, () -> push(HELLO));
        assertThat(in(acme, () -> push(HELLO))).isEqualTo(new BlobInfo(HELLO_DIGEST, HELLO.length));
        try (Stream<Digest> blobs = in(acme, store::list)) {
            assertThat(blobs).containsExactly(HELLO_DIGEST);
        }
    }

    @Test
    void readsRanges() throws Exception {
        in(acme, () -> push(HELLO));

        assertThat(in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(7, 10))))
                .isEqualTo("kiln".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(5, 11))))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    void storesAnEmptyBlob() throws Exception {
        BlobInfo info = in(acme, () -> push(new byte[0]));
        assertThat(info.size()).isZero();
        assertThat(in(acme, () -> readAll(info.digest(), null))).isEmpty();
    }

    @Test
    void neverSharesBlobsOrUploadsAcrossOrganizations() throws Exception {
        UploadId acmeUpload = in(acme, () -> {
            push(HELLO);
            return store.createUpload();
        });

        in(globex, () -> {
            assertThat(store.stat(HELLO_DIGEST)).isEmpty();
            assertThat(store.read(HELLO_DIGEST, null)).isEmpty();
            assertThat(store.delete(HELLO_DIGEST)).isFalse();
            try (Stream<Digest> blobs = store.list()) {
                assertThat(blobs).isEmpty();
            }
            assertThatThrownBy(() -> store.uploadSize(acmeUpload)).isInstanceOf(UploadNotFoundException.class);
            assertThatThrownBy(() -> store.append(acmeUpload, 0, stream("x"), 10))
                    .isInstanceOf(UploadNotFoundException.class);
            assertThatThrownBy(() -> store.commit(acmeUpload, HELLO_DIGEST))
                    .isInstanceOf(UploadNotFoundException.class);
            return null;
        });
        assertThat(in(acme, () -> store.stat(HELLO_DIGEST))).isPresent();
    }

    @Test
    void refusesToWorkWithoutAnOrganization() {
        assertThatIllegalStateException().isThrownBy(() -> store.stat(HELLO_DIGEST));
        assertThatIllegalStateException().isThrownBy(store::createUpload);
    }

    @Test
    void deletesBlobsAndUploads() throws Exception {
        in(acme, () -> {
            push(HELLO);
            assertThat(store.delete(HELLO_DIGEST)).isTrue();
            assertThat(store.stat(HELLO_DIGEST)).isEmpty();

            UploadId upload = store.createUpload();
            store.abortUpload(upload);
            assertThatThrownBy(() -> store.uploadSize(upload)).isInstanceOf(UploadNotFoundException.class);
            return null;
        });
    }

    @Test
    void acceptsAnUploadOfExactlyTheLimit() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            assertThat(store.append(upload, 0, stream("12345"), 10)).isEqualTo(5);
            assertThat(store.append(upload, 5, stream("67890"), 10)).isEqualTo(10);
            assertThatThrownBy(() -> store.append(upload, 10, stream("1"), 10))
                    .isInstanceOf(BlobTooLargeException.class);
            assertThat(store.uploadSize(upload)).isEqualTo(10);
            return null;
        });
    }

    @Test
    void anEmptyChunkChangesNothing() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            assertThat(store.append(upload, 0, stream(""), 0)).isZero();
            assertThat(store.append(upload, 0, stream("x"), 1)).isEqualTo(1);
            return null;
        });
    }

    @Test
    void reassemblesLargeUploadsFromRandomChunks() throws Exception {
        Random random = new Random(7);
        byte[] content = new byte[1024 * 1024 + 13];
        random.nextBytes(content);

        BlobInfo info = in(acme, () -> {
            UploadId upload = store.createUpload();
            int offset = 0;
            while (offset < content.length) {
                int length = Math.min(content.length - offset, 1 + random.nextInt(200_000));
                store.append(upload, offset, new ByteArrayInputStream(content, offset, length), content.length);
                offset += length;
            }
            return store.commit(upload, Digest.sha256(content));
        });

        assertThat(info.size()).isEqualTo(content.length);
        assertThat(in(acme, () -> readAll(info.digest(), null))).isEqualTo(content);
        assertThat(in(acme, () -> readAll(info.digest(), new ByteRange(1000, 70_000))))
                .isEqualTo(Arrays.copyOfRange(content, 1000, 70_001));
    }

    @Test
    void verifiesSha512Digests() throws Exception {
        Digester digester = Digester.of(DigestAlgorithm.SHA512);
        digester.update(HELLO, 0, HELLO.length);
        Digest sha512 = digester.digest();

        BlobInfo info = in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
            return store.commit(upload, sha512);
        });

        assertThat(info.digest()).isEqualTo(sha512);
        assertThat(in(acme, () -> readAll(sha512, null))).isEqualTo(HELLO);
        assertThat(in(acme, () -> store.stat(HELLO_DIGEST))).isEmpty();
    }

    @Test
    void aCommittedUploadIsGone() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
            store.commit(upload, HELLO_DIGEST);

            assertThatThrownBy(() -> store.commit(upload, HELLO_DIGEST)).isInstanceOf(UploadNotFoundException.class);
            assertThatThrownBy(() -> store.append(upload, HELLO.length, stream("x"), 100))
                    .isInstanceOf(UploadNotFoundException.class);
            return null;
        });
        assertNoUploadsLeft(acme);
    }

    @Test
    void anAbortedOrUnknownUploadIsNotFound() throws Exception {
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.abortUpload(upload);
            store.abortUpload(upload);
            assertThatThrownBy(() -> store.commit(upload, HELLO_DIGEST)).isInstanceOf(UploadNotFoundException.class);
            UploadId unknown = UploadId.random();
            assertThatThrownBy(() -> store.uploadSize(unknown)).isInstanceOf(UploadNotFoundException.class);
            assertThatThrownBy(() -> store.append(unknown, 0, stream("x"), 10))
                    .isInstanceOf(UploadNotFoundException.class);
            return null;
        });
    }

    @Test
    void aMismatchedCommitLeavesNoBlobBehind() throws Exception {
        Digest claimed = Digest.sha256("something else".getBytes(StandardCharsets.UTF_8));
        in(acme, () -> {
            UploadId upload = store.createUpload();
            store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
            assertThatThrownBy(() -> store.commit(upload, claimed)).isInstanceOf(DigestMismatchException.class);
            assertThat(store.stat(claimed)).isEmpty();
            assertThat(store.stat(HELLO_DIGEST)).isEmpty();
            return null;
        });
    }

    @Test
    void readsSingleBytesAndWholeRanges() throws Exception {
        in(acme, () -> push(HELLO));
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(0, 0)))).containsExactly('h');
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(10, 10)))).containsExactly('n');
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(0, HELLO.length - 1))))
                .isEqualTo(HELLO);
        assertThatThrownBy(() -> in(acme, () -> readAll(HELLO_DIGEST, new ByteRange(HELLO.length, HELLO.length))))
                .isInstanceOf(RangeNotSatisfiableException.class)
                .satisfies(e ->
                        assertThat(((RangeNotSatisfiableException) e).size()).isEqualTo(HELLO.length));
    }

    @Test
    void reportsTheReadWindow() throws Exception {
        in(acme, () -> push(HELLO));
        in(acme, () -> {
            try (BlobRead read = store.read(HELLO_DIGEST, new ByteRange(2, 5)).orElseThrow()) {
                assertThat(read.blobSize()).isEqualTo(HELLO.length);
                assertThat(read.offset()).isEqualTo(2);
                assertThat(read.length()).isEqualTo(4);
            }
            return null;
        });
    }

    @Test
    void transfersToChannelsThatAcceptOnlyAFewBytesAtATime() throws Exception {
        byte[] content = new byte[200_000];
        // Seeded on purpose, so the content is the same on every run; nothing here is a secret.
        // nosemgrep: java.lang.security.audit.crypto.weak-random.weak-random
        new Random(3).nextBytes(content);
        Digest digest = in(acme, () -> push(content)).digest();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel trickle = new WritableByteChannel() {
            @Override
            public int write(ByteBuffer source) {
                int n = Math.min(7, source.remaining());
                for (int i = 0; i < n; i++) {
                    out.write(source.get());
                }
                return n;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {}
        };
        in(acme, () -> {
            try (BlobRead read = store.read(digest, null).orElseThrow()) {
                read.transferTo(trickle);
            }
            return null;
        });
        assertThat(out.toByteArray()).isEqualTo(content);
    }

    @Test
    void uploadsSurviveARestart() throws Exception {
        UploadId upload = in(acme, () -> {
            UploadId id = store.createUpload();
            store.append(id, 0, stream("hello, "), 100);
            return id;
        });

        BlobStore restarted = newStore();

        BlobInfo info = in(acme, () -> {
            assertThat(restarted.uploadSize(upload)).isEqualTo(7);
            restarted.append(upload, 7, stream("kiln"), 100);
            return restarted.commit(upload, HELLO_DIGEST);
        });
        assertThat(info).isEqualTo(new BlobInfo(HELLO_DIGEST, HELLO.length));
        assertThat(in(acme, () -> store.stat(HELLO_DIGEST))).contains(info);
    }

    @Test
    void concurrentChunksAtTheSameOffsetApplyExactlyOnce() throws Exception {
        UploadId upload = in(acme, store::createUpload);
        int writers = 32;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < writers; i++) {
                results.add(executor.submit(() -> in(acme, () -> {
                    start.await();
                    try {
                        store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
                        return true;
                    } catch (UploadOffsetException e) {
                        return false;
                    }
                })));
            }
            start.countDown();
            int applied = 0;
            for (Future<Boolean> result : results) {
                applied += result.get() ? 1 : 0;
            }
            assertThat(applied).isEqualTo(1);
        }
        assertThat(in(acme, () -> store.commit(upload, HELLO_DIGEST)).size()).isEqualTo(HELLO.length);
    }

    @Test
    void concurrentPushesOfTheSameContentAllSucceed() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BlobInfo>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> in(acme, () -> {
                    UploadId upload = store.createUpload();
                    store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
                    start.await();
                    return store.commit(upload, HELLO_DIGEST);
                })));
            }
            start.countDown();
            for (Future<BlobInfo> result : results) {
                assertThat(result.get()).isEqualTo(new BlobInfo(HELLO_DIGEST, HELLO.length));
            }
        }
        assertThat(in(acme, () -> readAll(HELLO_DIGEST, null))).isEqualTo(HELLO);
        assertNoUploadsLeft(acme);
    }

    @Test
    void anotherOrganizationCannotAbortOrDeleteYourWork() throws Exception {
        UploadId acmeUpload = in(acme, () -> {
            push(HELLO);
            UploadId id = store.createUpload();
            store.append(id, 0, stream("hello"), 100);
            return id;
        });

        in(globex, () -> {
            store.abortUpload(acmeUpload);
            store.delete(HELLO_DIGEST);
            return null;
        });

        assertThat(in(acme, () -> store.uploadSize(acmeUpload))).isEqualTo(5);
        assertThat(in(acme, () -> store.stat(HELLO_DIGEST))).isPresent();
    }

    @Test
    void theSameContentIsStoredSeparatelyPerOrganization() throws Exception {
        in(acme, () -> push(HELLO));
        in(globex, () -> push(HELLO));
        in(globex, () -> store.delete(HELLO_DIGEST));

        assertThat(in(acme, () -> readAll(HELLO_DIGEST, null))).isEqualTo(HELLO);
        assertThat(in(globex, () -> store.stat(HELLO_DIGEST))).isEmpty();
    }

    @Test
    void listsEveryAlgorithm() throws Exception {
        Digester digester = Digester.of(DigestAlgorithm.SHA512);
        digester.update(HELLO, 0, HELLO.length);
        Digest sha512 = digester.digest();
        in(acme, () -> {
            push(HELLO);
            UploadId upload = store.createUpload();
            store.append(upload, 0, new ByteArrayInputStream(HELLO), 100);
            return store.commit(upload, sha512);
        });
        try (Stream<Digest> listed = in(acme, store::list)) {
            assertThat(listed).containsExactlyInAnyOrder(HELLO_DIGEST, sha512);
        }
    }

    @Test
    void listIsEmptyForANewOrganization() throws Exception {
        try (Stream<Digest> listed = in(globex, store::list)) {
            assertThat(listed).isEmpty();
        }
    }

    protected BlobInfo push(byte[] content) throws IOException {
        UploadId upload = store.createUpload();
        store.append(upload, 0, new ByteArrayInputStream(content), Long.MAX_VALUE);
        return store.commit(upload, Digest.sha256(content));
    }

    protected byte[] readAll(Digest digest, ByteRange range) throws IOException {
        try (BlobRead read = store.read(digest, range).orElseThrow()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            read.transferTo(Channels.newChannel(out));
            assertThat(out.size()).isEqualTo(read.length());
            return out.toByteArray();
        }
    }

    protected static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    protected static <T> T in(Organization organization, Callable<T> work) throws Exception {
        return OrganizationContext.call(organization, work::call);
    }
}
