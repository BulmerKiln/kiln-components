/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.service;

import com.bulmerkiln.core.annotation.UntrustedInput;
import com.bulmerkiln.crypto.CryptoException;
import com.bulmerkiln.crypto.KeyProvider;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.util.Set;

/**
 * A KEK kept as a Tink JSON keyset in a file, such as a mounted Kubernetes Secret (CRYPTO-6). The
 * keyset's primary key wraps; every key in it unwraps, so rotation is adding a key and making it
 * primary. Every key must be AES-256-GCM.
 */
public final class FileKeyProvider implements KeyProvider {

    /** Far larger than any keyset of a few dozen keys. */
    static final int MAX_SIZE = 64 * 1024;

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Tink's AEAD primitives are unavailable", e);
        }
    }

    private final Aead kek;
    private final String kekId;

    private FileKeyProvider(KeysetHandle keyset) throws GeneralSecurityException {
        this.kek = keyset.getPrimitive(RegistryConfiguration.get(), Aead.class);
        this.kekId = Integer.toUnsignedString(keyset.getPrimary().getId());
    }

    /**
     * Loads the keyset at {@code file}. With {@code generateIfMissing}, a missing file is created
     * first with one new key, readable only by its owner; otherwise a missing file stops the boot,
     * so a mistyped path never yields a fresh KEK that can open nothing (CRYPTO-7).
     */
    @UntrustedInput
    public static FileKeyProvider load(Path file, boolean generateIfMissing) {
        if (generateIfMissing && !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            generate(file);
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw invalid(file, "isn't a readable file");
            }
            if (Files.size(file) > MAX_SIZE) {
                throw invalid(file, "is too large to be a keyset");
            }
            requireNotWritableByOthers(file);
            KeysetHandle keyset = TinkJsonProtoKeysetFormat.parseKeyset(
                    Files.readString(file, StandardCharsets.UTF_8), InsecureSecretKeyAccess.get());
            for (int i = 0; i < keyset.size(); i++) {
                if (!(keyset.getAt(i).getKey().getParameters() instanceof AesGcmParameters gcm)
                        || gcm.getKeySizeBytes() != 32) {
                    throw invalid(file, "holds a key that isn't AES-256-GCM");
                }
            }
            return new FileKeyProvider(keyset);
        } catch (IOException e) {
            throw invalid(file, "can't be read");
        } catch (GeneralSecurityException e) {
            throw invalid(file, "isn't a Tink JSON keyset");
        }
    }

    @Override
    public Aead kek() {
        return kek;
    }

    @Override
    public String kekId() {
        return kekId;
    }

    private static void generate(Path file) {
        try {
            String keyset = TinkJsonProtoKeysetFormat.serializeKeyset(
                    KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM), InsecureSecretKeyAccess.get());
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null && Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class)) {
                Files.createFile(
                        file,
                        PosixFilePermissions.asFileAttribute(
                                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
            } else {
                Files.createFile(file);
            }
            Files.writeString(file, keyset, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (FileAlreadyExistsException e) {
            // Another replica created it first; load reads theirs.
        } catch (IOException e) {
            throw invalid(file, "can't be created");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("A KEK couldn't be generated", e);
        }
    }

    /** A KEK anyone else can rewrite isn't the operator's; a world-readable mount is the Secret default. */
    private static void requireNotWritableByOthers(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw invalid(file, "is writable by others than its owner");
            }
        }
    }

    private static CryptoException invalid(Path file, String problem) {
        return new CryptoException("kiln.crypto.kek-file " + file + " " + problem);
    }
}
