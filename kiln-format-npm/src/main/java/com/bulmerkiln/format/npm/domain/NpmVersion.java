/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.domain.SemanticVersion;
import com.bulmerkiln.format.npm.NpmException;
import lombok.Value;

/** A package version: a strict SemVer 2.0.0 version (FMT-NPM-8, FMT-13). */
@Value
public final class NpmVersion {

    private final String value;

    public NpmVersion(String value) {
        if (!isVersion(value)) {
            throw NpmException.invalid("Invalid version");
        }
        this.value = value;
    }

    static boolean isVersion(String value) {
        return SemanticVersion.isValid(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
