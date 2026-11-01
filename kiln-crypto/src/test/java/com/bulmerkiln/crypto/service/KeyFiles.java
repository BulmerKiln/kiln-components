/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.service;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

/** KEK files for tests, written the way an operator's tooling (Tinkey) writes them. */
final class KeyFiles {

    private KeyFiles() {}

    /** A provider over a new KEK file, created if it doesn't exist yet. */
    static FileKeyProvider provider(Path file) {
        return FileKeyProvider.load(file, true);
    }

    /** Adds a new AES-256-GCM key to the keyset and makes it primary, as a rotation does. */
    static void rotate(Path file) {
        try {
            KeysetHandle current = TinkJsonProtoKeysetFormat.parseKeyset(
                    Files.readString(file, StandardCharsets.UTF_8), InsecureSecretKeyAccess.get());
            KeysetHandle rotated = KeysetHandle.newBuilder(current)
                    .addEntry(KeysetHandle.generateEntryFromParameters(PredefinedAeadParameters.AES256_GCM)
                            .withRandomId()
                            .makePrimary())
                    .build();
            Files.writeString(file, TinkJsonProtoKeysetFormat.serializeKeyset(rotated, InsecureSecretKeyAccess.get()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A keyset of another AEAD than AES-256-GCM. */
    static String aes128Keyset() throws GeneralSecurityException {
        return TinkJsonProtoKeysetFormat.serializeKeyset(
                KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM), InsecureSecretKeyAccess.get());
    }
}
