/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import java.io.Serial;
import java.util.List;

/** The schema breaks a rule that keeps organizations apart, so Kiln refuses to start (DB-6). */
public final class SchemaViolationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SchemaViolationException(List<String> problems) {
        super("The database schema is not organization-safe:\n- " + String.join("\n- ", problems));
    }
}
