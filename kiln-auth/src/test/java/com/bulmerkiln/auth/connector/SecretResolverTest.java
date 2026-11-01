/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.SecretRef;
import com.bulmerkiln.auth.connector.util.SecretResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretResolverTest {

    @Test
    void resolvesFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secret");
        Files.writeString(file, "file-secret\n");
        SecretRef ref = new SecretRef.File(file);
        assertThat(SecretResolver.resolve(ref)).isEqualTo("file-secret");
    }

    @Test
    void stripsWhitespaceFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secret");
        Files.writeString(file, "  padded-secret  \n");
        SecretRef ref = new SecretRef.File(file);
        assertThat(SecretResolver.resolve(ref)).isEqualTo("padded-secret");
    }

    @Test
    void failsWhenEnvVarIsMissing() {
        SecretRef ref = new SecretRef.Env("KILN_DEFINITELY_NOT_SET_VAR_XYZ");
        assertThatThrownBy(() -> SecretResolver.resolve(ref)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenFileIsMissing() {
        SecretRef ref = new SecretRef.File(Path.of("/nonexistent/kiln-secret"));
        assertThatThrownBy(() -> SecretResolver.resolve(ref)).isInstanceOf(IllegalStateException.class);
    }

    /** AUTH-19: an unreadable client secret stops the boot with an error naming the connector. */
    @Test
    void namesTheConnectorWhenAClientSecretCantBeRead() {
        SecretRef ref = new SecretRef.Env("KILN_DEFINITELY_NOT_SET_VAR_XYZ");
        assertThatThrownBy(() -> SecretResolver.resolveClientSecret(new ConnectorId("corp"), ref))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Connector corp: client-secret: ");
    }

    @Test
    void resolvesAReadableClientSecret(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secret");
        Files.writeString(file, "s3cret\n");
        assertThat(SecretResolver.resolveClientSecret(new ConnectorId("corp"), new SecretRef.File(file)))
                .isEqualTo("s3cret");
    }
}
