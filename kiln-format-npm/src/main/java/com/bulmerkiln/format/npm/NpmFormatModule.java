/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.RefusalResponse;
import com.bulmerkiln.format.util.FormatJsonUtils;
import java.util.Map;

/** The npm registry, on {@code /npm/}. Errors are npm's {@code {"error": ...}} body. */
public final class NpmFormatModule implements FormatModule {

    public static final ArtifactFormat FORMAT = new ArtifactFormat("npm");

    @Override
    public ArtifactFormat format() {
        return FORMAT;
    }

    @Override
    public String pathSegment() {
        return "npm";
    }

    @Override
    public RefusalResponse render(Refusal refusal) {
        return RefusalResponse.of("application/json", FormatJsonUtils.write(Map.of("error", refusal.getMessage())));
    }
}
