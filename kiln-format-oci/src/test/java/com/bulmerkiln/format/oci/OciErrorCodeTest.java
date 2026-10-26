/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OciErrorCodeTest {

    /** The codes and statuses distribution/distribution answers with, which clients are tested against. */
    @ParameterizedTest
    @CsvSource({
        "BLOB_UNKNOWN, 404",
        "BLOB_UPLOAD_INVALID, 400",
        "BLOB_UPLOAD_UNKNOWN, 404",
        "DIGEST_INVALID, 400",
        "MANIFEST_BLOB_UNKNOWN, 400",
        "MANIFEST_INVALID, 400",
        "MANIFEST_UNKNOWN, 404",
        "NAME_INVALID, 400",
        "NAME_UNKNOWN, 404",
        "SIZE_INVALID, 400",
        "UNAUTHORIZED, 401",
        "DENIED, 403",
        "UNSUPPORTED, 405",
        "TOOMANYREQUESTS, 429"
    })
    void matchesTheSpec(OciErrorCode code, int status) {
        assertThat(code.status()).isEqualTo(status);
        assertThat(code.message()).isNotBlank();
    }

    @Test
    void noCodeIsAServerError() {
        // A 5xx makes docker and buildkit retry endlessly (FMT-1).
        assertThat(Arrays.stream(OciErrorCode.values()).mapToInt(OciErrorCode::status))
                .allMatch(s -> s >= 400 && s < 500);
    }
}
