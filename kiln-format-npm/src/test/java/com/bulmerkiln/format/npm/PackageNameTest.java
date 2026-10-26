/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackageNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"lodash", "@acme/ui", "@acme/ui.core", "a", "-dash", "my_pkg", "@a-b/c.d-e_f", "123"})
    void acceptsValidNames(String name) {
        assertThat(PackageName.parse(name)).hasToString(name);
    }

    @Test
    void splitsScopes() {
        PackageName name = PackageName.parse("@acme/ui");
        assertThat(name.scope()).isEqualTo("acme");
        assertThat(name.name()).isEqualTo("ui");
        assertThat(name.urlEncoded()).isEqualTo("@acme%2fui");
        assertThat(PackageName.parse("lodash").urlEncoded()).isEqualTo("lodash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"@acme%2fui", "@acme%2Fui", "@acme/ui"})
    void acceptsScopesEncodedEitherWay(String raw) {
        assertThat(PackageName.fromPath(raw)).isEqualTo(PackageName.parse("@acme/ui"));
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
        assertThatThrownBy(() -> PackageName.fromPath(raw)).isInstanceOf(NpmException.class);
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
        assertThatThrownBy(() -> PackageName.parse(name))
                .isInstanceOfSatisfying(
                        NpmException.class, e -> assertThat(e.status()).isEqualTo(400));
    }

    @Test
    void limitsTheLengthIncludingTheScope() {
        assertThat(PackageName.parse("a".repeat(214))).isNotNull();
        assertThatThrownBy(() -> PackageName.parse("a".repeat(215))).isInstanceOf(NpmException.class);
        assertThat(PackageName.parse("@" + "s".repeat(10) + "/" + "a".repeat(202)))
                .isNotNull();
        assertThatThrownBy(() -> PackageName.parse("@" + "s".repeat(10) + "/" + "a".repeat(203)))
                .isInstanceOf(NpmException.class);
    }

    @FuzzTest
    void parsingOnlyFailsWithNpmErrors(FuzzedDataProvider data) {
        try {
            PackageName name = PackageName.fromPath(data.consumeRemainingAsString());
            assertThat(PackageName.parse(name.toString())).isEqualTo(name);
        } catch (NpmException expected) {
            // The only failure a name may cause.
        }
    }
}
