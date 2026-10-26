/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.Refusal;
import com.bulmerkiln.format.RefusalBody;

/** The Maven repository, on {@code /maven/}. Errors are plain text, since Maven and Gradle show the body. */
public final class MavenFormatModule implements FormatModule {

    public static final ArtifactFormat FORMAT = new ArtifactFormat("maven");

    @Override
    public ArtifactFormat format() {
        return FORMAT;
    }

    @Override
    public String pathSegment() {
        return "maven";
    }

    @Override
    public RefusalBody render(Refusal refusal) {
        // Maven over HTTP/2 has no reason phrase, so the reason is always in the body too.
        return RefusalBody.of("text/plain; charset=utf-8", refusal.message());
    }
}
