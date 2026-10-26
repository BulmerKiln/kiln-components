/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.FormatModuleContract;
import com.bulmerkiln.format.FormatResponse;
import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;
import org.junit.jupiter.api.Test;

class MavenFormatModuleTest extends FormatModuleContract {

    @Override
    protected FormatModule module() {
        return new MavenFormatModule();
    }

    @Test
    void usesTheFormatsOwnErrorBody() {
        FormatResponse response = FormatResponse.refusal(module(), Refusal.of(Reason.NOT_FOUND, "no such package"));
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.contentType()).isEqualTo("text/plain; charset=utf-8");
        assertThat(response.body()).isEqualTo("no such package");
    }
}
