/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.domain;

import java.util.regex.Pattern;

/**
 * Strict SemVer 2.0.0 versions, as npm and Cargo use them: {@code major.minor.patch} without
 * leading zeros, then optional pre-release and build labels, at most 256 characters (FMT-13).
 */
public final class SemanticVersion {

    /** {@code major.minor.patch}, without leading zeros. */
    private static final Pattern CORE =
            Pattern.compile("(0|[1-9][0-9]{0,15})\\.(0|[1-9][0-9]{0,15})\\.(0|[1-9][0-9]{0,15})");

    private static final String LABEL_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";

    private SemanticVersion() {}

    /** The core by pattern; the pre-release and build labels by hand, so nothing can backtrack. */
    public static boolean isValid(String value) {
        if (value.length() > 256) {
            return false;
        }
        int plus = value.indexOf('+');
        String withoutBuild = plus < 0 ? value : value.substring(0, plus);
        if (plus >= 0 && !isLabels(value.substring(plus + 1))) {
            return false;
        }
        int dash = withoutBuild.indexOf('-');
        String core = dash < 0 ? withoutBuild : withoutBuild.substring(0, dash);
        return CORE.matcher(core).matches() && (dash < 0 || isLabels(withoutBuild.substring(dash + 1)));
    }

    /** Dot-separated identifiers of ASCII letters, digits and hyphens, none empty. */
    private static boolean isLabels(String labels) {
        for (String label : labels.split("\\.", -1)) {
            if (label.isEmpty() || !label.chars().allMatch(c -> LABEL_CHARS.indexOf(c) >= 0)) {
                return false;
            }
        }
        return true;
    }
}
