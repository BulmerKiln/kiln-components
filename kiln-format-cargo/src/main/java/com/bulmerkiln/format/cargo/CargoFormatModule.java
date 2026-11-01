/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.RefusalResponse;
import com.bulmerkiln.format.util.FormatJsonUtils;
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
    public RefusalResponse render(Refusal refusal) {
        return RefusalResponse.of(
                "application/json",
                FormatJsonUtils.write(Map.of("errors", List.of(Map.of("detail", refusal.getMessage())))));
    }
}
