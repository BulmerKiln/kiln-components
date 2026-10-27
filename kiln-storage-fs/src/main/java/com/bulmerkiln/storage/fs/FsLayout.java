/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.fs;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.DigestAlgorithm;
import com.bulmerkiln.core.digest.InvalidDigestException;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.storage.UploadId;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Where the filesystem store keeps everything (STO-FS-1). Every path is built from a validated
 * {@link Digest}, {@link UploadId} or organization ID, never from client text.
 */
final class FsLayout {

    private final Path root;

    /**
     * Creates the root if needed, and checks the filesystem renames atomically (STO-FS-2).
     *
     * @throws java.nio.file.AtomicMoveNotSupportedException if it doesn't
     */
    FsLayout(Path root) throws IOException {
        this.root = Files.createDirectories(root.toAbsolutePath().normalize());
        checkAtomicRename();
    }

    Path blobPath(Digest digest) {
        return blobDirectory(digest).resolve(digest.encoded());
    }

    Path blobDirectory(Digest digest) {
        return organizationRoot()
                .resolve("blobs")
                .resolve(digest.algorithm().id())
                .resolve(digest.encoded().substring(0, 2));
    }

    Path uploadsDirectory() {
        return organizationRoot().resolve("_uploads");
    }

    Path uploadPath(UploadId upload) {
        return uploadsDirectory().resolve(upload.toString());
    }

    /** Every blob of the current organization. Anything else in the tree is skipped (STO-FS-4). */
    @MustBeClosed
    Stream<Digest> list() throws IOException {
        Path blobs = organizationRoot().resolve("blobs");
        if (!Files.isDirectory(blobs)) {
            return Stream.empty();
        }
        return Files.find(blobs, 3, (path, attributes) -> attributes.isRegularFile())
                .flatMap(path -> toDigest(blobs.relativize(path)).stream());
    }

    /** Makes a rename durable. Not every platform can open a directory, so this is best effort. */
    static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // Windows can't open directories; NTFS journals the rename itself.
        }
    }

    private Path organizationRoot() {
        return root.resolve(OrganizationContext.current().id().toString());
    }

    /** {@code <algorithm>/<ab>/<hex>}, or empty for anything that isn't a blob. */
    private static Optional<Digest> toDigest(Path relative) {
        if (relative.getNameCount() != 3) {
            return Optional.empty();
        }
        String algorithm = relative.getName(0).toString();
        String shard = relative.getName(1).toString();
        String hex = relative.getName(2).toString();
        if (shard.length() != 2 || !hex.startsWith(shard)) {
            return Optional.empty();
        }
        try {
            return DigestAlgorithm.fromId(algorithm).map(a -> new Digest(a, hex));
        } catch (InvalidDigestException e) {
            return Optional.empty();
        }
    }

    private void checkAtomicRename() throws IOException {
        Path probe = root.resolve(".kiln-rename-check-" + UUID.randomUUID());
        Path renamed = root.resolve(probe.getFileName() + "-renamed");
        Files.createFile(probe);
        try {
            Files.move(probe, renamed, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(probe);
            Files.deleteIfExists(renamed);
        }
    }
}
