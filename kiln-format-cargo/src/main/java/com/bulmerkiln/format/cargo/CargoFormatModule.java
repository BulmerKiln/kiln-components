/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatJson;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusalBody;
import java.util.List;
import java.util.Map;

/** The Cargo registry, on {@code /cargo/}. Errors are Cargo's {@code {"errors": [{"detail": ...}]}} body. */
public final class CargoFormatModule implements FormatModule {

    public static final ArtifactFormat FORMAT = new ArtifactFormat("cargo");

    @Override
    public ArtifactFormat format() {
        return FORMAT;
    }

    @Override
    public String pathSegment() {
        return "cargo";
    }

    @Override
    public RefusalBody render(Refusal refusal) {
        return RefusalBody.of(
                "application/json", FormatJson.write(Map.of("errors", List.of(Map.of("detail", refusal.message())))));
    }
}
