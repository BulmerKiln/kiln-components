/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrgTest {

    static final List<String> RESERVED = List.copyOf(Org.RESERVED_SLUGS);

    @Test
    void orgIdRoundTripsThroughItsString() {
        OrgId id = new OrgId(UUID.randomUUID());
        assertThat(OrgId.of(id.toString())).isEqualTo(id);
        assertThat(id).hasToString(id.value().toString());
    }

    @Test
    void orgIdRequiresAValue() {
        assertThatNullPointerException().isThrownBy(() -> new OrgId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> OrgId.of("not-a-uuid"));
    }

    @Test
    void orgRequiresAnId() {
        assertThatNullPointerException().isThrownBy(() -> new Org(null, "acme"));
    }

    @Test
    void slugsCanBeThirtyNineCharactersButNotForty() {
        assertThat(Org.isValidSlug("a".repeat(39))).isTrue();
        assertThat(Org.isValidSlug("a".repeat(40))).isFalse();
    }

    @ParameterizedTest
    @FieldSource("RESERVED")
    void everyReservedSlugIsRefused(String slug) {
        assertThat(Org.isValidSlug(slug)).isFalse();
    }

    @Test
    void reservedSlugsAreMatchedExactly() {
        assertThat(Org.isValidSlug("v2-team")).isTrue();
        assertThat(Org.isValidSlug("npmjs")).isTrue();
    }

    /** Lookalikes and encodings that could make one organization's address read as another's. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "аcme", // Cyrillic a
                "acmе", // Cyrillic e
                "ac​me", // zero-width space
                "acme\u0000", // NUL
                "ａcme", // fullwidth a
                "acme ",
                " acme",
                "ac me",
                "acme\n",
                "acme\t",
                "%61cme",
                "acme%2F",
                "acme/globex",
                "acme\\globex",
                "acme.",
                "ACME",
                "İstanbul" // dotted capital I, which lowercases unpredictably
            })
    void refusesLookalikesAndEncodings(String slug) {
        assertThat(Org.isValidSlug(slug)).isFalse();
    }
}
