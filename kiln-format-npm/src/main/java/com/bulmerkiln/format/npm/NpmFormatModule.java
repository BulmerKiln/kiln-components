/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatJson;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusalBody;
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
    public RefusalBody render(Refusal refusal) {
        return RefusalBody.of("application/json", FormatJson.write(Map.of("error", refusal.message())));
    }
}
