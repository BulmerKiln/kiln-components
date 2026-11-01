/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import com.bulmerkiln.scan.constant.Severity;
import com.bulmerkiln.scan.util.BoundsUtils;
import java.util.List;
import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One finding from one scanner.
 */
@Value
public final class Finding {

    private final Kind kind;

    /** The advisory or rule ID, such as {@code CVE-2024-3094}, {@code GHSA-...} or {@code MAL-...}. */
    private final String id;

    /** Other IDs for the same issue, used to deduplicate across scanners. */
    private final List<String> aliases;

    /** The affected package as a Package URL; absent for secrets and misconfiguration. */
    private final @Nullable String purl;

    private final Severity severity;

    private final @Nullable String fixedVersion;

    /** A short description; never the secret itself, which the worker must not send. */
    private final @Nullable String title;

    public static final int MAX_ALIASES = 50;

    public enum Kind {
        VULNERABILITY,
        MALWARE,
        SECRET,
        MISCONFIGURATION
    }

    public Finding(
            Kind kind,
            String id,
            List<String> aliases,
            @Nullable String purl,
            Severity severity,
            @Nullable String fixedVersion,
            @Nullable String title) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        BoundsUtils.identifier(id, "id");
        this.aliases = List.copyOf(aliases);
        if (this.aliases.size() > MAX_ALIASES) {
            throw new IllegalArgumentException("More than " + MAX_ALIASES + " aliases");
        }
        this.aliases.forEach(alias -> BoundsUtils.identifier(alias, "alias"));
        if (purl != null && (!purl.startsWith("pkg:") || purl.length() > 2048)) {
            throw new IllegalArgumentException("purl must be a Package URL");
        }
        if (fixedVersion != null) {
            BoundsUtils.text(fixedVersion, "fixedVersion", 255);
        }
        if (title != null) {
            BoundsUtils.text(title, "title", 512);
        }
        this.kind = kind;
        this.id = id;
        this.purl = purl;
        this.severity = severity;
        this.fixedVersion = fixedVersion;
        this.title = title;
    }
}
