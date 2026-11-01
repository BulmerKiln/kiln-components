/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.autoconfigure;

import java.nio.file.Path;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envelope-encryption settings, under {@code kiln.crypto}.
 */
@ConfigurationProperties("kiln.crypto")
@Value
public final class CryptoProperties {

    /** The Tink JSON keyset holding the key-encryption key; required. */
    private final @Nullable Path kekFile;

    /**
     * Create {@code kekFile} with a new key when it doesn't exist, for evaluation installs; off by
     * default, so a mistyped path stops the boot.
     */
    private final @Nullable Boolean generateKekIfMissing;

    public Path kekFileOrFail() {
        if (kekFile == null) {
            throw new IllegalArgumentException("kiln.crypto.kek-file is required: the key-encryption key's keyset");
        }
        return kekFile;
    }

    public boolean generateKekIfMissingOrDefault() {
        return Boolean.TRUE.equals(generateKekIfMissing);
    }
}
