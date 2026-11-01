/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import java.util.Objects;
import java.util.Optional;
import lombok.Value;

/**
 * Finds the organization a request is for, from the artifact path in its URL, such as {@code
 * acme/api} in {@code /v2/acme/api/manifests/1.0}. The service that assembles Kiln provides it: one
 * that serves a single organization returns it for every path, unchanged; one that addresses
 * organizations by path takes the first segment.
 */
public interface OrganizationResolver {

    /** The organization {@code path} belongs to and the artifact path within it, or empty if none. */
    Optional<Resolution> resolve(String path);

    /** An organization, and what's left of the path once the organization is taken from it. */
    @Value
    static final class Resolution {

        private final Organization organization;

        private final String path;

        public Resolution(Organization organization, String path) {
            Objects.requireNonNull(organization, "organization");
            Objects.requireNonNull(path, "path");
            this.organization = organization;
            this.path = path;
        }
    }
}
