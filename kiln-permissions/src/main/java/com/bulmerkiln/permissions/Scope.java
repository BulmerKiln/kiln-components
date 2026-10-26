/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a role applies: the whole organization, one namespace, or one repository or package in a
 * namespace. A scope covers itself and everything inside it.
 */
public record Scope(@Nullable String namespace, @Nullable String repository) {

    public Scope {
        if (namespace != null && namespace.isBlank()) {
            throw new IllegalArgumentException("A namespace must not be blank");
        }
        if (repository != null && (repository.isBlank() || namespace == null)) {
            throw new IllegalArgumentException("A repository is named within a namespace");
        }
    }

    public static Scope organization() {
        return new Scope(null, null);
    }

    public static Scope namespace(String namespace) {
        return new Scope(namespace, null);
    }

    public static Scope repository(String namespace, String repository) {
        return new Scope(namespace, repository);
    }

    public boolean isOrganization() {
        return namespace == null;
    }

    /** Whether a role granted here applies to {@code target}. Names match exactly. */
    public boolean covers(Scope target) {
        if (namespace == null) {
            return true;
        }
        if (!Objects.equals(namespace, target.namespace)) {
            return false;
        }
        return repository == null || repository.equals(target.repository);
    }
}
