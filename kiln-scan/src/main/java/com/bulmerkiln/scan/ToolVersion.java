/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A tool's version, such as {@code 0.56.2} or {@code v8.21.2}, for adapters' supported ranges. */
public record ToolVersion(int major, int minor, int patch) implements Comparable<ToolVersion> {

    private static final Pattern VERSION =
            Pattern.compile("v?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:[-+][0-9A-Za-z.+-]{0,64})?");

    private static final Comparator<ToolVersion> ORDER = Comparator.comparingInt(ToolVersion::major)
            .thenComparingInt(ToolVersion::minor)
            .thenComparingInt(ToolVersion::patch);

    /** Empty for anything that isn't a plain version, which no adapter supports. */
    public static Optional<ToolVersion> parse(String version) {
        Matcher m = VERSION.matcher(version);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ToolVersion(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
    }

    /**
     * Whether {@code version} is at least {@code minimum} and has the same major version, or for
     * {@code 0.x} tools, whose minor releases may change their output, the same minor version or
     * later minors up to {@code maxMinor}.
     */
    public static boolean supported(String version, ToolVersion minimum, int maxMinor) {
        return parse(version)
                .filter(v ->
                        v.compareTo(minimum) >= 0 && v.major == minimum.major && (v.major > 0 || v.minor <= maxMinor))
                .isPresent();
    }

    @Override
    public int compareTo(ToolVersion other) {
        return ORDER.compare(this, other);
    }
}
