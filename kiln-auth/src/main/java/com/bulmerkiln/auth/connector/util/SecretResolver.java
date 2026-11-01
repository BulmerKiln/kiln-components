/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.util;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.SecretRef;
import java.io.IOException;
import java.nio.file.Files;

/** Resolves a {@link SecretRef} to its value, from the environment or a file. */
public final class SecretResolver {

    private SecretResolver() {}

    /**
     * Reads a connector's client secret at boot, so a missing or unreadable one stops the boot with
     * an error naming the connector and the setting.
     */
    public static String resolveClientSecret(ConnectorId id, SecretRef ref) {
        try {
            return resolve(ref);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Connector " + id + ": client-secret: " + e.getMessage(), e);
        }
    }

    /** Reads the secret from the referenced environment variable or file. */
    public static String resolve(SecretRef ref) {
        return switch (ref) {
            case SecretRef.Env env -> {
                String value = System.getenv(env.getName());
                if (value == null || value.isBlank()) {
                    throw new IllegalStateException("Environment variable " + env.getName() + " is not set");
                }
                yield value;
            }
            case SecretRef.File file -> {
                try {
                    yield Files.readString(file.getPath()).strip();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read secret file " + file.getPath(), e);
                }
            }
        };
    }
}
