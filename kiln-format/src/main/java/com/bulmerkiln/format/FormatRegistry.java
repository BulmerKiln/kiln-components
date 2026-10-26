/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.org.Org;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The enabled formats, checked so no two claim one format ID or one route. */
public final class FormatRegistry {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    private final Map<ArtifactFormat, FormatModule> byFormat = new LinkedHashMap<>();
    private final Map<String, FormatModule> bySegment = new LinkedHashMap<>();

    public FormatRegistry(Collection<FormatModule> modules) {
        for (FormatModule module : modules) {
            String segment = module.pathSegment();
            if (!SEGMENT.matcher(segment).matches() || !Org.RESERVED_SLUGS.contains(segment)) {
                throw new IllegalStateException("Format " + module.format() + " uses route /" + segment
                        + "/, which is not a reserved organization slug; reserve it in Org.RESERVED_SLUGS first");
            }
            if (byFormat.putIfAbsent(module.format(), module) != null) {
                throw new IllegalStateException("Two modules both implement format " + module.format());
            }
            if (bySegment.putIfAbsent(segment, module) != null) {
                throw new IllegalStateException(
                        "Formats " + bySegment.get(segment).format() + " and " + module.format() + " both claim route /"
                                + segment + "/");
            }
        }
    }

    public List<FormatModule> formats() {
        return List.copyOf(byFormat.values());
    }

    public Optional<FormatModule> get(ArtifactFormat format) {
        return Optional.ofNullable(byFormat.get(format));
    }

    /** The format serving routes under {@code /<segment>/}. */
    public Optional<FormatModule> forSegment(String segment) {
        return Optional.ofNullable(bySegment.get(segment));
    }
}
