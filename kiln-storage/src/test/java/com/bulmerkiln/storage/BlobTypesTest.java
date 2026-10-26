/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.digest.Digest;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlobTypesTest {

    private static final Digest EMPTY = Digest.sha256(new byte[0]);

    @Test
    void blobInfoRefusesNegativeSizesAndMissingDigests() {
        assertThat(new BlobInfo(EMPTY, 0).size()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new BlobInfo(EMPTY, -1));
        assertThatNullPointerException().isThrownBy(() -> new BlobInfo(null, 0));
    }

    @Test
    void uploadIdsAreRandomVersionFourUuids() {
        Set<UploadId> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UploadId id = UploadId.random();
            assertThat(id.value().version()).isEqualTo(4);
            assertThat(seen.add(id)).isTrue();
        }
    }

    @Test
    void uploadIdsRoundTripInCanonicalForm() {
        UploadId id = UploadId.random();
        assertThat(UploadId.parse(id.toString())).isEqualTo(id);
        assertThatNullPointerException().isThrownBy(() -> new UploadId(null));
    }

    /** Upload IDs come from URLs; anything but the canonical form must not reach a storage path. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "1-1-1-1-1", // UUID.fromString accepts this
                "0000000-0000-0000-0000-0000000000000",
                "../00000000-0000-0000-0000-000000000000",
                "00000000-0000-0000-0000-000000000000/..",
                "00000000-0000-0000-0000-00000000000%",
                "00000000-0000-0000-0000-000000000000\u0000",
                " 00000000-0000-0000-0000-00000000000",
                "{00000000-0000-0000-0000-000000000000}",
                "000000000000000000000000000000000000",
                "00000000-0000-0000-0000-00000000000\uFF10"
            })
    void uploadIdsMustBeCanonical(String value) {
        assertThatThrownBy(() -> UploadId.parse(value)).isInstanceOf(UploadNotFoundException.class);
    }

    @Test
    void uppercaseUploadIdsAreRefused() {
        String upper = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        assertThatThrownBy(() -> UploadId.parse(upper)).isInstanceOf(UploadNotFoundException.class);
    }

    @Test
    void exceptionsCarryWhatTheResponseNeeds() {
        assertThat(new UploadOffsetException(5, 3).expected()).isEqualTo(5);
        assertThat(new BlobTooLargeException(10).limit()).isEqualTo(10);
        assertThat(new RangeNotSatisfiableException(42).size()).isEqualTo(42);
        Digest other = Digest.sha256(new byte[1]);
        assertThat(new DigestMismatchException(EMPTY, other))
                .hasMessageContaining(EMPTY.toString())
                .hasMessageContaining(other.toString());
    }

    @FuzzTest
    void uploadIdParsingOnlyAcceptsCanonicalIds(FuzzedDataProvider data) {
        String value = data.consumeRemainingAsString();
        try {
            assertThat(UploadId.parse(value)).hasToString(value);
        } catch (UploadNotFoundException expected) {
            // Anything but a canonical ID is a session that doesn't exist.
        }
    }
}
