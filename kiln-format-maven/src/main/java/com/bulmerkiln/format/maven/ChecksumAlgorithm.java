/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import java.util.Optional;

/** The checksum sidecar files Kiln serves for every file, computed on the server. */
public enum ChecksumAlgorithm {
    MD5(".md5", "MD5"),
    SHA1(".sha1", "SHA-1"),
    SHA256(".sha256", "SHA-256"),
    SHA512(".sha512", "SHA-512");

    private final String suffix;
    private final String jcaName;

    ChecksumAlgorithm(String suffix, String jcaName) {
        this.suffix = suffix;
        this.jcaName = jcaName;
    }

    public String suffix() {
        return suffix;
    }

    public String jcaName() {
        return jcaName;
    }

    static Optional<ChecksumAlgorithm> ofFileName(String fileName) {
        for (ChecksumAlgorithm algorithm : values()) {
            if (fileName.endsWith(algorithm.suffix) && fileName.length() > algorithm.suffix.length()) {
                return Optional.of(algorithm);
            }
        }
        return Optional.empty();
    }
}
