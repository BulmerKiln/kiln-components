/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.crypto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.crypto.KeyProvider;
import com.bulmerkiln.crypto.service.EnvelopeService;
import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CryptoAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class));

    @TempDir
    Path dir;

    /** CRYPTO-8: the file KEK by default, sealing and opening through the envelope service. */
    @Test
    void sealsWithTheFileKek() {
        runner.withPropertyValues(
                        "kiln.crypto.kek-file=" + dir.resolve("kek.json"), "kiln.crypto.generate-kek-if-missing=true")
                .run(context -> {
                    EnvelopeService envelope = context.getBean(EnvelopeService.class);
                    byte[] secret = "x".getBytes(StandardCharsets.UTF_8);
                    assertThat(envelope.open(envelope.seal(secret, "c"), "c")).isEqualTo(secret);
                });
    }

    /** CRYPTO-8: without a KEK file Kiln doesn't start, and a missing one is never made silently. */
    @Test
    void requiresAKekFile() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("kiln.crypto.kek-file=" + dir.resolve("missing.json"))
                .run(context -> assertThat(context).hasFailed());
        assertThat(dir.resolve("missing.json")).doesNotExist();
    }

    /** CRYPTO-8: an assembly's own KeyProvider, such as a KMS, replaces the file. */
    @Test
    void usesTheAssemblysKeyProvider() {
        KeyProvider own = new KeyProvider() {
            @Override
            public Aead kek() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String kekId() {
                return "kms";
            }
        };
        runner.withBean(KeyProvider.class, () -> own)
                .run(context -> assertThat(context.getBean(KeyProvider.class)).isSameAs(own));
    }
}
