/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.Map;

/** Runs the contract against a minimal format, so the suite itself is tested here. */
class ExampleFormatContractTest extends FormatModuleContract {

    @Override
    protected FormatModule module() {
        return FormatRulesTest.module("npm", "npm", Map.of());
    }
}
