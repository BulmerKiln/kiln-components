/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A repository name such as {@code acme/team/app}, validated to the distribution-spec grammar
 * (spec.md, "Pulling manifests"), with Kiln's length limit.
 */
@Value
public final class OciRepositoryName {

    private final String value;

    /** Kiln's limit (FMT-OCI-1). */
    public static final int MAX_LENGTH = 255;

    private static final Pattern SLASH = Pattern.compile("/");

    public OciRepositoryName(String value) {
        if (value.length() > MAX_LENGTH || !isName(value)) {
            throw new OciException(OciErrorCode.NAME_INVALID, "Invalid repository name");
        }
        this.value = value;
    }

    /** Components joined by single slashes. */
    private static boolean isName(String value) {
        for (String component : SLASH.split(value, -1)) {
            if (!isComponent(component)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code [a-z0-9]+} runs joined by {@code .}, {@code _}, {@code __} or one or more {@code -},
     * scanned once from left to right.
     */
    private static boolean isComponent(String component) {
        int length = component.length();
        if (length == 0 || !isAlphanumeric(component.charAt(0)) || !isAlphanumeric(component.charAt(length - 1))) {
            return false;
        }
        int i = 0;
        while (i < length) {
            int separatorEnd = skip(component, i, false);
            if (separatorEnd > i && !isSeparator(component.substring(i, separatorEnd))) {
                return false;
            }
            i = skip(component, separatorEnd, true);
        }
        return true;
    }

    /** The index after the run of alphanumeric (or non-alphanumeric) characters starting at {@code from}. */
    private static int skip(String component, int from, boolean alphanumeric) {
        int i = from;
        while (i < component.length() && isAlphanumeric(component.charAt(i)) == alphanumeric) {
            i++;
        }
        return i;
    }

    private static boolean isSeparator(String separator) {
        return separator.equals(".")
                || separator.equals("_")
                || separator.equals("__")
                || separator.chars().allMatch(c -> c == '-');
    }

    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    @Override
    public String toString() {
        return value;
    }
}
