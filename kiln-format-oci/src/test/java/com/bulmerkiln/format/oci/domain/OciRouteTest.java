/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.autoconfigure.OciProperties;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FMT-OCI-14: routes are read from the end of the path. */
class OciRouteTest {

    private static final String DIGEST = "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void readsEveryRoute() {
        assertThat(OciRoute.parse("a/b/manifests/1.0"))
                .isEqualTo(new OciRoute.Manifest(new OciRepositoryName("a/b"), new OciReference.Tag("1.0")));
        assertThat(OciRoute.parse("a/blobs/" + DIGEST)).isInstanceOf(OciRoute.Blob.class);
        assertThat(OciRoute.parse("a/blobs/uploads/")).isEqualTo(new OciRoute.Uploads(new OciRepositoryName("a")));
        assertThat(OciRoute.parse("a/blobs/uploads/x")).isEqualTo(new OciRoute.Upload(new OciRepositoryName("a"), "x"));
        assertThat(OciRoute.parse("a/tags/list")).isEqualTo(new OciRoute.Tags(new OciRepositoryName("a")));
        assertThat(OciRoute.parse("a/referrers/" + DIGEST)).isInstanceOf(OciRoute.Referrers.class);
    }

    /** Names may contain the route words; the last one decides. */
    @ParameterizedTest
    @CsvSource({
        "manifests/manifests/1, manifests",
        "x/blobs/y/manifests/latest, x/blobs/y",
        "tags/list/tags/list, tags/list",
        "a/blobs/uploads/b/blobs/uploads/, a/blobs/uploads/b"
    })
    void takesTheLastRouteKeyword(String path, String name) {
        assertThat(OciRoute.parse(path).repository().value()).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource({"''", "manifests/1", "a/", "a/manifests/", "a/tags", "a/blobs/uploads"})
    void refusesWhatIsNoRoute(String path) {
        assertThatThrownBy(() -> OciRoute.parse(path)).isInstanceOf(RefusedException.class);
    }

    @Test
    void refusesOverlongPaths() {
        assertThatThrownBy(() -> OciRoute.parse("a/".repeat(400) + "tags/list"))
                .isInstanceOf(OciException.class)
                .hasMessage("Invalid repository name");
    }

    @FuzzTest(maxDuration = "10s")
    void neverThrowsAnythingButARefusal(String path) {
        if (path == null) {
            return;
        }
        try {
            OciRoute.parse(path);
        } catch (RefusedException e) {
            assertThat(e.status()).isBetween(400, 499);
        }
    }

    @Test
    void defaultsItsLimits() {
        OciProperties defaults = new OciProperties(null, null, null, null);

        assertThat(defaults.maxBlobSizeOrDefault()).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(defaults.maxManifestSizeOrDefault()).isEqualTo(OciManifestParser.DEFAULT_MAX_SIZE);
        assertThat(defaults.tagPageSizeOrDefault()).isEqualTo(1000);
        assertThat(new OciProperties(true, 1L, 2, 3).tagPageSizeOrDefault()).isEqualTo(3);
    }
}
