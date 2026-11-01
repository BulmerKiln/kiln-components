/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.crypto.CryptoException;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileKeyProviderTest {

    @TempDir
    Path dir;

    /** CRYPTO-7: generating creates a keyset only its owner can read, once; a reload keeps the key. */
    @Test
    void generatesAKeysetOnlyWhenAskedTo() throws Exception {
        Path kek = dir.resolve("kek.json");

        FileKeyProvider generated = FileKeyProvider.load(kek, true);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(kek)))
                .isEqualTo("rw-------");
        assertThat(FileKeyProvider.load(kek, true).kekId()).isEqualTo(generated.kekId());
        assertThat(FileKeyProvider.load(kek, false).kekId()).isEqualTo(generated.kekId());
        assertThatThrownBy(() -> FileKeyProvider.load(dir.resolve("typo.json"), false))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("kiln.crypto.kek-file")
                .hasMessageContaining("isn't a readable file");
        assertThat(dir.resolve("typo.json")).doesNotExist();
    }

    /** CRYPTO-6: only an AES-256-GCM Tink keyset, of bounded size, that no one else can rewrite. */
    @Test
    void refusesAnythingButASafeKeyset() throws Exception {
        Path notJson = Files.writeString(dir.resolve("text.json"), "s3cr3t-value");
        Path weak = Files.writeString(dir.resolve("aes128.json"), KeyFiles.aes128Keyset());
        Path huge = Files.write(dir.resolve("huge.json"), new byte[FileKeyProvider.MAX_SIZE + 1]);
        Path shared = dir.resolve("shared.json");
        FileKeyProvider.load(shared, true);
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rw-rw-r--"));
        Path readable = dir.resolve("readable.json");
        FileKeyProvider.load(readable, true);
        Files.setPosixFilePermissions(readable, PosixFilePermissions.fromString("rw-r--r--"));

        assertThatThrownBy(() -> FileKeyProvider.load(notJson, false))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("isn't a Tink JSON keyset")
                .hasMessageNotContaining("s3cr3t");
        assertThatThrownBy(() -> FileKeyProvider.load(weak, false)).hasMessageContaining("isn't AES-256-GCM");
        assertThatThrownBy(() -> FileKeyProvider.load(huge, false)).hasMessageContaining("too large");
        assertThatThrownBy(() -> FileKeyProvider.load(shared, false)).hasMessageContaining("writable by others");
        assertThatThrownBy(() -> FileKeyProvider.load(dir, false)).hasMessageContaining("isn't a readable file");
        assertThat(FileKeyProvider.load(readable, false).kekId()).isNotBlank();
    }

    /** CRYPTO-6: whatever bytes a keyset file holds, loading fails only with a CryptoException. */
    @FuzzTest
    void loadingAnythingFailsOnlyAsACryptoException(FuzzedDataProvider data) throws Exception {
        Path file = dir.resolve("fuzz-keyset.json");
        Files.write(file, data.consumeRemainingAsBytes());
        try {
            FileKeyProvider.load(file, false);
        } catch (CryptoException expected) {
            // The only failure a keyset file may cause.
        }
    }
}
