/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One finding from one scanner.
 *
 * @param id the advisory or rule ID, such as {@code CVE-2024-3094}, {@code GHSA-...} or {@code MAL-...}
 * @param aliases other IDs for the same issue, used to deduplicate across scanners
 * @param purl the affected package as a Package URL; absent for secrets and misconfiguration
 * @param title a short description; never the secret itself, which the worker must not send
 */
public record Finding(
        Kind kind,
        String id,
        List<String> aliases,
        @Nullable String purl,
        Severity severity,
        @Nullable String fixedVersion,
        @Nullable String title) {

    public static final int MAX_ALIASES = 50;

    public enum Kind {
        VULNERABILITY,
        MALWARE,
        SECRET,
        MISCONFIGURATION
    }

    public Finding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Bounds.identifier(id, "id");
        aliases = List.copyOf(aliases);
        if (aliases.size() > MAX_ALIASES) {
            throw new IllegalArgumentException("More than " + MAX_ALIASES + " aliases");
        }
        aliases.forEach(alias -> Bounds.identifier(alias, "alias"));
        if (purl != null && (!purl.startsWith("pkg:") || purl.length() > 2048)) {
            throw new IllegalArgumentException("purl must be a Package URL");
        }
        if (fixedVersion != null) {
            Bounds.text(fixedVersion, "fixedVersion", 255);
        }
        if (title != null) {
            Bounds.text(title, "title", 512);
        }
    }
}
