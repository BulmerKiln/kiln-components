/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.FormatModuleContract;
import com.bulmerkiln.format.FormatResponse;
import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;
import org.junit.jupiter.api.Test;

class OciFormatModuleTest extends FormatModuleContract {

    private final OciFormatModule oci = new OciFormatModule();

    @Override
    protected FormatModule module() {
        return oci;
    }

    @Test
    void usesTheSpecEnvelopeAndVersionHeader() {
        FormatResponse response =
                FormatResponse.refusal(oci, new OciException(OciErrorCode.MANIFEST_UNKNOWN, "no such tag").refusal());
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body())
                .isEqualTo("{\"errors\":[{\"code\":\"MANIFEST_UNKNOWN\",\"message\":\"no such tag\"}]}");
        assertThat(response.headers()).containsEntry("Docker-Distribution-API-Version", "registry/2.0");
    }

    @Test
    void usesACodeHintOnlyWhenItAgreesWithTheReason() {
        assertThat(OciErrorCode.forRefusal(Refusal.of(Reason.NOT_FOUND, "x").withCode("BLOB_UNKNOWN")))
                .isEqualTo(OciErrorCode.BLOB_UNKNOWN);
        // A block hinted as BLOB_UNKNOWN would read as "missing", and a mirror would try elsewhere.
        assertThat(OciErrorCode.forRefusal(Refusal.of(Reason.BLOCKED, "blocked").withCode("BLOB_UNKNOWN")))
                .isEqualTo(OciErrorCode.DENIED);
        assertThat(OciErrorCode.forRefusal(Refusal.of(Reason.INVALID, "x").withCode("NOT_A_CODE")))
                .isEqualTo(OciErrorCode.MANIFEST_INVALID);
    }

    @Test
    void mapsEveryReasonToACodeWithASensibleStatus() {
        for (Reason reason : Reason.values()) {
            Refusal refusal = reason == Reason.UNAUTHORIZED
                    ? Refusal.unauthorized("auth", "Bearer realm=\"x\"")
                    : Refusal.of(reason, "x");
            assertThat(OciErrorCode.forRefusal(refusal).status()).isBetween(400, 499);
        }
        assertThat(OciErrorCode.forRefusal(Refusal.of(Reason.OVER_QUOTA, "quota")))
                .isEqualTo(OciErrorCode.DENIED);
        assertThat(OciErrorCode.forRefusal(Refusal.of(Reason.CONFLICT, "immutable tag")))
                .isEqualTo(OciErrorCode.DENIED);
    }

    @Test
    void parserErrorsKeepTheirCodes() {
        OciException e = new OciException(OciErrorCode.SIZE_INVALID, "too big");
        assertThat(e.status()).isEqualTo(400);
        assertThat(OciErrorCode.forRefusal(e.refusal())).isEqualTo(OciErrorCode.SIZE_INVALID);
    }
}
