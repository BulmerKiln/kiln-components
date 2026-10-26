/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class DigesterTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.US_ASCII);

    /** FIPS 180-2 test vectors for "abc". */
    @Test
    void matchesPublishedTestVectors() {
        assertThat(digest(DigestAlgorithm.SHA256, ABC))
                .hasToString("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(digest(DigestAlgorithm.SHA512, ABC))
                .hasToString("sha512:ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");
    }

    @Test
    void streamsLargeContentAcrossBufferBoundaries() throws Exception {
        byte[] content = new byte[3 * 64 * 1024 + 17];
        new Random(42).nextBytes(content);

        Digester streamed = Digester.of(DigestAlgorithm.SHA256);
        streamed.update(new ByteArrayInputStream(content));

        assertThat(streamed.digest()).isEqualTo(Digest.sha256(content));
    }

    @Property
    void chunkingDoesNotChangeTheDigest(@ForAll byte[] content, @ForAll @IntRange(min = 1, max = 64) int chunk) {
        Digester digester = Digester.of(DigestAlgorithm.SHA256);
        for (int offset = 0; offset < content.length; offset += chunk) {
            digester.update(content, offset, Math.min(chunk, content.length - offset));
        }
        assertThat(digester.digest()).isEqualTo(Digest.sha256(content));
    }

    @Test
    void algorithmsAreLookedUpByExactId() {
        assertThat(DigestAlgorithm.fromId("sha256")).contains(DigestAlgorithm.SHA256);
        assertThat(DigestAlgorithm.fromId("sha512")).contains(DigestAlgorithm.SHA512);
        assertThat(DigestAlgorithm.fromId("SHA256")).isEmpty();
        assertThat(DigestAlgorithm.fromId("sha-256")).isEmpty();
        assertThat(DigestAlgorithm.fromId("")).isEmpty();
    }

    private static Digest digest(DigestAlgorithm algorithm, byte[] content) {
        Digester digester = Digester.of(algorithm);
        digester.update(content, 0, content.length);
        return digester.digest();
    }
}
