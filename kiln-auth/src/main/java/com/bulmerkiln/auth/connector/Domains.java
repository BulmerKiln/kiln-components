/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Email domains as connectors declare them for IdP discovery. */
public final class Domains {

    private static final int MAX_LENGTH = 253;
    private static final int MAX_LABEL = 63;
    private static final Pattern DOT = Pattern.compile("\\.");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9-]+");

    private Domains() {}

    /** Lowercases and validates the domains, refusing anything that isn't a plain hostname. */
    public static Set<String> normalize(Collection<String> domains) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String domain : domains) {
            String lower = domain.toLowerCase(Locale.ROOT);
            if (!isDomain(lower)) {
                throw new IllegalArgumentException("Invalid domain: " + domain);
            }
            normalized.add(lower);
        }
        return Set.copyOf(normalized);
    }

    /**
     * An ASCII hostname of at most 253 characters with at least two labels, each 1 to 63
     * characters that don't start or end with a hyphen, and a top-level label of at least two
     * characters starting with a letter. IDNs are configured in their {@code xn--} form.
     */
    private static boolean isDomain(String domain) {
        if (domain.isEmpty() || domain.length() > MAX_LENGTH) {
            return false;
        }
        String[] labels = DOT.split(domain, -1);
        for (String label : labels) {
            if (!isLabel(label)) {
                return false;
            }
        }
        String top = labels[labels.length - 1];
        return labels.length >= 2 && top.length() >= 2 && top.charAt(0) >= 'a' && top.charAt(0) <= 'z';
    }

    private static boolean isLabel(String label) {
        return !label.isEmpty()
                && label.length() <= MAX_LABEL
                && LABEL.matcher(label).matches()
                && label.charAt(0) != '-'
                && label.charAt(label.length() - 1) != '-';
    }
}
