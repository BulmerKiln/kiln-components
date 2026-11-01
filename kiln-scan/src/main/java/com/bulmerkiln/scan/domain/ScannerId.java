/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A scanner's identifier, such as {@code trivy} or {@code osv-scanner}. It's an open value: each
 * adapter in kiln-scanner-service names its own, so adding a scanner never changes the protocol.
 * It travels as a plain JSON string.
 */
@Value
public final class ScannerId implements Comparable<ScannerId> {

    private final String id;

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public ScannerId(String id) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid scanner ID");
        }
        this.id = id;
    }

    @JsonCreator
    public static ScannerId of(String id) {
        return new ScannerId(id);
    }

    @JsonValue
    public String getId() {
        return id;
    }

    @Override
    public int compareTo(ScannerId other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id;
    }
}
