/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import com.bulmerkiln.core.annotation.UntrustedInput;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * An organization: the isolation unit. Its slug can be the first path segment of artifact
 * addresses, so it must be a valid OCI repository-name component and must never collide with
 * Kiln's own routes (CORE-4).
 */
@Value
public final class Organization {

    private final OrganizationId id;

    private final String slug;

    /** Lowercase letters, digits and single inner hyphens, at most 39 characters. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,38}");

    /** Versioned with the route inventory: new routes only ever take names already listed here. */
    public static final Set<String> RESERVED_SLUGS = Set.of(
            "v2",
            "npm",
            "maven",
            "helm",
            "cargo",
            "ui",
            "api",
            "auth",
            "token",
            "scim",
            "login",
            ".well-known",
            "metrics",
            "healthz",
            "admin",
            "status",
            "docs",
            "static",
            "assets",
            "kiln",
            "bulmer");

    public Organization(OrganizationId id, String slug) {
        Objects.requireNonNull(id, "id");
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid organization slug: " + slug);
        }
        this.id = id;
        this.slug = slug;
    }

    @UntrustedInput
    public static boolean isValidSlug(String slug) {
        return SLUG.matcher(slug).matches() && !RESERVED_SLUGS.contains(slug);
    }
}
