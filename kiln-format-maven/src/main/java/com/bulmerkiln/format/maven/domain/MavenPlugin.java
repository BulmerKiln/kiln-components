/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A Maven plugin in its group's metadata: the goal prefix that lets {@code mvn <prefix>:<goal>}
 * find {@code artifactId} (Maven "Repository Metadata", group level).
 */
public record MavenPlugin(
        String artifactId, String prefix, @Nullable String name) {

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    /** Longer than any real artifactId. */
    static final int MAX_ARTIFACT_ID = 255;

    /** Longer than any plugin's display name; it's shown, never interpreted. */
    static final int MAX_NAME = 256;

    public MavenPlugin {
        if (artifactId.length() > MAX_ARTIFACT_ID
                || !MavenPath.SEGMENT.matcher(artifactId).matches()) {
            throw invalid("Invalid plugin artifactId");
        }
        if (!PREFIX.matcher(prefix).matches()) {
            throw invalid("Invalid plugin prefix");
        }
        if (name != null && (name.length() > MAX_NAME || name.chars().anyMatch(Character::isISOControl))) {
            throw invalid("Invalid plugin name");
        }
    }

    private static RefusedException invalid(String message) {
        return new RefusedException(Refusal.of(Reason.INVALID, message));
    }
}
