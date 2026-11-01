/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.npm.NpmException;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NpmPackageNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"lodash", "@acme/ui", "@acme/ui.core", "a", "-dash", "my_pkg", "@a-b/c.d-e_f", "123"})
    void acceptsValidNames(String name) {
        assertThat(NpmPackageName.parse(name)).hasToString(name);
    }

    @Test
    void splitsScopes() {
        NpmPackageName name = NpmPackageName.parse("@acme/ui");
        assertThat(name.getScope()).isEqualTo("acme");
        assertThat(name.getName()).isEqualTo("ui");
        assertThat(name.urlEncoded()).isEqualTo("@acme%2fui");
        assertThat(NpmPackageName.parse("lodash").urlEncoded()).isEqualTo("lodash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"@acme%2fui", "@acme%2Fui", "@acme/ui"})
    void acceptsScopesEncodedEitherWay(String raw) {
        assertThat(NpmPackageName.fromPath(raw)).isEqualTo(NpmPackageName.parse("@acme/ui"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "@acme%252fui", // double encoding
                "%40acme%2fui",
                "lo%64ash",
                "@acme%2f..%2fglobex",
                "@acme/ui%00",
                "%2e%2e",
                "@acme%2fui%2fx"
            })
    void refusesOtherEscapesAndTraversal(String raw) {
        assertThatThrownBy(() -> NpmPackageName.fromPath(raw)).isInstanceOf(NpmException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "@",
                "@/",
                "@acme",
                "@acme/",
                "@/ui",
                "Lodash",
                "@Acme/ui",
                ".hidden",
                "_private",
                "@.acme/ui",
                "@acme/.ui",
                "..",
                "a b",
                " lodash",
                "lodash ",
                "lo~dash",
                "lo*dash",
                "lo'dash",
                "lo!dash",
                "lo(dash)",
                "@acme/ui/extra",
                "node_modules",
                "favicon.ico",
                "l\u00f6dash",
                "\u0430cme",
                "lodash\n",
                "lo/dash",
                "lo\\dash",
                "lo:dash"
            })
    void refusesInvalidNames(String name) {
        assertThatThrownBy(() -> NpmPackageName.parse(name))
                .isInstanceOfSatisfying(
                        NpmException.class, e -> assertThat(e.status()).isEqualTo(400));
    }

    @Test
    void limitsTheLengthIncludingTheScope() {
        assertThat(NpmPackageName.parse("a".repeat(214))).isNotNull();
        assertThatThrownBy(() -> NpmPackageName.parse("a".repeat(215))).isInstanceOf(NpmException.class);
        assertThat(NpmPackageName.parse("@" + "s".repeat(10) + "/" + "a".repeat(202)))
                .isNotNull();
        assertThatThrownBy(() -> NpmPackageName.parse("@" + "s".repeat(10) + "/" + "a".repeat(203)))
                .isInstanceOf(NpmException.class);
    }

    @FuzzTest
    void parsingOnlyFailsWithNpmErrors(FuzzedDataProvider data) {
        try {
            NpmPackageName name = NpmPackageName.fromPath(data.consumeRemainingAsString());
            assertThat(NpmPackageName.parse(name.toString())).isEqualTo(name);
        } catch (NpmException expected) {
            // The only failure a name may cause.
        }
    }
}
