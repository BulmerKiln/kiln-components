/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Maps each tool's severity vocabulary onto the protocol's, the same way for every adapter. */
public final class Severities {

    private Severities() {}

    /**
     * A severity name, case-insensitively: {@code critical}, {@code high}, {@code medium} or
     * GitHub's {@code moderate}, {@code low} or Grype's {@code negligible}. Anything else is unknown.
     */
    public static Severity named(@Nullable String name) {
        if (name == null) {
            return Severity.UNKNOWN;
        }
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "critical" -> Severity.CRITICAL;
            case "high" -> Severity.HIGH;
            case "medium", "moderate" -> Severity.MEDIUM;
            case "low", "negligible" -> Severity.LOW;
            default -> Severity.UNKNOWN;
        };
    }

    /** A CVSS base score, with the CVSS v3 rating bands. */
    public static Severity cvss(double score) {
        if (Double.isNaN(score) || score <= 0 || score > 10) {
            return Severity.UNKNOWN;
        }
        return score >= 9
                ? Severity.CRITICAL
                : score >= 7 ? Severity.HIGH : score >= 4 ? Severity.MEDIUM : Severity.LOW;
    }
}
