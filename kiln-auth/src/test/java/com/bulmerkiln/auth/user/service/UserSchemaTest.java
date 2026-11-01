/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.database.service.SchemaLinter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class UserSchemaTest {

    /** AUTH-25: this module's tables pass the schema linter, which stops the boot otherwise. */
    @Test
    void schemaIsOrganizationSafe() {
        try (UserDatabase db = new UserDatabase()) {
            List<String> problems = db.database().instanceTransaction(SchemaLinter::problems);

            assertThat(problems).isEmpty();
        }
    }
}
