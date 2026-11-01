/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.util;

import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.storage.BlobRead;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maven's checksums: MD5, SHA-1, SHA-256 and SHA-512, as lowercase hex (FMT-MVN-12). */
public final class MavenChecksumUtils {

    private MavenChecksumUtils() {}

    /** A checksum of bytes in hand, such as generated metadata, as lowercase hex. */
    public static String checksum(byte[] bytes, MavenChecksumAlgorithm algorithm) {
        return HexFormat.of().formatHex(messageDigest(algorithm.jcaName()).digest(bytes));
    }

    /** A checksum of a stored file's bytes, read through once. */
    public static String checksum(BlobRead read, MavenChecksumAlgorithm algorithm) throws IOException {
        MessageDigest hash = messageDigest(algorithm.jcaName());
        read.transferTo(Channels.newChannel(new DigestOutputStream(OutputStream.nullOutputStream(), hash)));
        return HexFormat.of().formatHex(hash.digest());
    }

    /** A hash for every checksum Maven asks for, to take while a file streams in. */
    public static Map<MavenChecksumAlgorithm, MessageDigest> hashes() {
        Map<MavenChecksumAlgorithm, MessageDigest> hashes = new EnumMap<>(MavenChecksumAlgorithm.class);
        for (MavenChecksumAlgorithm algorithm : MavenChecksumAlgorithm.values()) {
            hashes.put(algorithm, messageDigest(algorithm.jcaName()));
        }
        return hashes;
    }

    /** The finished hash of one algorithm, read from a copy so it can be read again. */
    public static String hex(Map<MavenChecksumAlgorithm, MessageDigest> hashes, MavenChecksumAlgorithm algorithm) {
        MessageDigest hash = Objects.requireNonNull(hashes.get(algorithm), "every algorithm is hashed");
        try {
            return HexFormat.of().formatHex(((MessageDigest) hash.clone()).digest());
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Every JDK message digest can be cloned", e);
        }
    }

    /** The recorded checksum of one algorithm, or null when it wasn't taken. */
    public static @Nullable String pick(BlobChecksums checksums, MavenChecksumAlgorithm algorithm) {
        return switch (algorithm) {
            case MD5 -> checksums.getMd5();
            case SHA1 -> checksums.getSha1();
            case SHA256 -> checksums.getSha256();
            case SHA512 -> checksums.getSha512();
        };
    }

    /** Checksums with only one algorithm's, to record one computed later. */
    public static BlobChecksums only(MavenChecksumAlgorithm algorithm, String value) {
        return switch (algorithm) {
            case MD5 -> new BlobChecksums(value, null, null, null);
            case SHA1 -> new BlobChecksums(null, value, null, null);
            case SHA256 -> new BlobChecksums(null, null, value, null);
            case SHA512 -> new BlobChecksums(null, null, null, value);
        };
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides " + algorithm, e);
        }
    }
}
