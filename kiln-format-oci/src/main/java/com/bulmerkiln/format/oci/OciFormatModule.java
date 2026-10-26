/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatJson;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusalBody;
import java.util.List;
import java.util.Map;

/**
 * OCI Distribution Spec 1.1, on {@code /v2/}. Errors use the spec envelope, {@code {"errors":
 * [{"code", "message"}]}}.
 */
public final class OciFormatModule implements FormatModule {

    public static final ArtifactFormat FORMAT = new ArtifactFormat("oci");

    @Override
    public ArtifactFormat format() {
        return FORMAT;
    }

    @Override
    public String pathSegment() {
        return "v2";
    }

    @Override
    public RefusalBody render(Refusal refusal) {
        OciErrorCode code = OciErrorCode.forRefusal(refusal);
        String body = FormatJson.write(new Envelope(List.of(new Envelope.Item(code.name(), refusal.message()))));
        return new RefusalBody("application/json", body, Map.of("Docker-Distribution-API-Version", "registry/2.0"));
    }

    /** The spec's error envelope; records keep the field order clients expect. */
    private record Envelope(List<Item> errors) {

        private record Item(String code, String message) {}
    }
}
