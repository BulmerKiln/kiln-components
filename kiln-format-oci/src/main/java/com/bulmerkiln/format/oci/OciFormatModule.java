/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.RefusalResponse;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.dto.OciErrorResponse;
import com.bulmerkiln.format.util.FormatJsonUtils;
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
    public RefusalResponse render(Refusal refusal) {
        OciErrorCode code = OciErrorCode.forRefusal(refusal);
        String body = FormatJsonUtils.write(
                new OciErrorResponse(List.of(new OciErrorResponse.Item(code.name(), refusal.getMessage()))));
        return new RefusalResponse("application/json", body, Map.of("Docker-Distribution-API-Version", "registry/2.0"));
    }
}
