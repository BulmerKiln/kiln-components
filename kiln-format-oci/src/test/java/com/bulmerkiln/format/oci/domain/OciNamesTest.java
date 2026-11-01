/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OciNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"app", "acme/app", "acme/team/app", "a.b", "a_b", "a__b", "a---b", "acme/app-1.2_x"})
    void acceptsSpecRepositoryNames(String name) {
        assertThat(new OciRepositoryName(name)).hasToString(name);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "App",
                "acme//app",
                "/acme",
                "acme/",
                "..",
                "acme/../globex",
                "acme/%2fglobex",
                "a___b",
                "a..b",
                "-app",
                "app-",
                "acme/app:latest",
                "acme\\app"
            })
    void rejectsInvalidRepositoryNames(String name) {
        assertThatThrownBy(() -> new OciRepositoryName(name))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.NAME_INVALID));
    }

    @Test
    void limitsRepositoryNameLength() {
        assertThat(new OciRepositoryName("a".repeat(255))).isNotNull();
        assertThatThrownBy(() -> new OciRepositoryName("a".repeat(256))).isInstanceOf(OciException.class);
    }

    @Test
    void parsesTagsAndDigests() {
        assertThat(OciReference.parse("v1.2.3_rc-1")).isEqualTo(new OciReference.Tag("v1.2.3_rc-1"));
        Digest digest = Digest.sha256(new byte[0]);
        assertThat(OciReference.parse(digest.toString())).isEqualTo(new OciReference.ByDigest(digest));
        assertThat(OciReference.parse("a".repeat(128))).isInstanceOf(OciReference.Tag.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                ".hidden",
                "-dash",
                "a/b",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void rejectsInvalidTags(String tag) {
        assertThatThrownBy(() -> OciReference.parse(tag)).isInstanceOf(OciException.class);
    }

    @Test
    void reportsMalformedDigestReferences() {
        assertThatThrownBy(() -> OciReference.parse("sha256:nope"))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.DIGEST_INVALID));
    }

    /** Crafted names that might land in another organization's or repository's path. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "acme/%2e%2e/globex",
                "acme/..%2fglobex",
                "acme%2fglobex",
                "acme/./app",
                "./acme",
                "acme/app/",
                "acme\\u0000/app",
                "acme/app\\n",
                " acme/app",
                "acme /app",
                "\\u0430cme/app",
                "acme/\\uFF41pp",
                "acme/app?x=1",
                "acme/app#x",
                "acme/app;x",
                "acme:5000/app",
                "acme/app@sha256"
            })
    void rejectsTraversalAndInjection(String name) {
        assertThatThrownBy(() -> new OciRepositoryName(name)).isInstanceOf(OciException.class);
    }

    @Test
    void acceptsDeepNestingWithinTheLimit() {
        String deep = String.join("/", Collections.nCopies(127, "a"));
        assertThat(deep).hasSize(253);
        assertThat(new OciRepositoryName(deep)).hasToString(deep);
    }

    @Test
    void rejectsPathologicalNamesQuickly() {
        String backtracking = "a" + "-".repeat(250) + "!";
        String huge = "a".repeat(10_000_000);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThatThrownBy(() -> new OciRepositoryName(backtracking)).isInstanceOf(OciException.class);
            assertThatThrownBy(() -> new OciRepositoryName(huge)).isInstanceOf(OciException.class);
            assertThatThrownBy(() -> OciReference.parse(huge)).isInstanceOf(OciException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "_", "latest", "LATEST", "v1.0.0", "1.0.0-rc.1", "sha256-abc", "a_b.c-d", "__"})
    void acceptsValidTags(String tag) {
        assertThat(OciReference.parse(tag)).isEqualTo(new OciReference.Tag(tag)).hasToString(tag);
    }

    @ParameterizedTest
    @ValueSource(strings = {"latest\\n", "lat est", "latest\\u0000", "\\u0430pp", "v1+build", "v1/../latest", "a@b"})
    void rejectsTagInjection(String tag) {
        assertThatThrownBy(() -> new OciReference.Tag(tag)).isInstanceOf(OciException.class);
    }

    @Test
    void anythingWithAColonIsADigest() {
        assertThatThrownBy(() -> OciReference.parse("latest:1"))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.DIGEST_INVALID));
        assertThatThrownBy(() -> OciReference.parse("md5:d41d8cd98f00b204e9800998ecf8427e"))
                .isInstanceOfSatisfying(
                        OciException.class, e -> assertThat(e.code()).isEqualTo(OciErrorCode.DIGEST_INVALID));
        Digest digest = Digest.sha256(new byte[0]);
        assertThat(OciReference.parse(digest.toString())).hasToString(digest.toString());
    }

    @FuzzTest
    void parsingOnlyFailsWithRegistryErrors(FuzzedDataProvider data) {
        String name = data.consumeString(300);
        String reference = data.consumeRemainingAsString();
        try {
            assertThat(new OciRepositoryName(name).getValue()).doesNotContain("..", "//");
        } catch (OciException expected) {
            // Rejected names are always reported as registry errors.
        }
        try {
            OciReference.parse(reference);
        } catch (OciException expected) {
            // Same for references.
        }
    }
}
