/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.domain;

import java.util.regex.Pattern;
import lombok.Value;

/**
 * A connector's ID. It appears in the callback path ({@code /auth/<connector-id>/callback}) and in
 * the {@code kiln_last_idp} cookie, so it's limited to lowercase letters, digits and inner hyphens.
 */
@Value
public final class ConnectorId {

    private final String value;

    private static final Pattern GRAMMAR = Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,62}");

    public ConnectorId(String value) {
        if (!GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid connector ID: " + value);
        }
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
