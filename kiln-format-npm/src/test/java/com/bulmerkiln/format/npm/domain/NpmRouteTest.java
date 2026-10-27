/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.autoconfigure.NpmProperties;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FMT-NPM-7, FMT-NPM-8: routes below {@code /npm/}, and versions. */
class NpmRouteTest {

    private static final NpmPackageName UTIL = NpmPackageName.parse("@acme/util");

    @Test
    void readsEveryRoute() {
        assertThat(NpmRoute.parse("-/ping")).isEqualTo(new NpmRoute.Ping());
        assertThat(NpmRoute.parse("-/whoami")).isEqualTo(new NpmRoute.Whoami());
        assertThat(NpmRoute.parse("@acme%2futil")).isEqualTo(new NpmRoute.Packument(UTIL));
        assertThat(NpmRoute.parse("@acme/util")).isEqualTo(new NpmRoute.Packument(UTIL));
        assertThat(NpmRoute.parse("lodash")).isEqualTo(new NpmRoute.Packument(NpmPackageName.parse("lodash")));
        assertThat(NpmRoute.parse("@acme/util/1.0.0")).isEqualTo(new NpmRoute.Version(UTIL, new NpmVersion("1.0.0")));
        assertThat(NpmRoute.parse("@acme%2futil/-/util-1.0.0.tgz"))
                .isEqualTo(new NpmRoute.Tarball(UTIL, "util-1.0.0.tgz"));
        assertThat(NpmRoute.parse("-/package/@acme%2futil/dist-tags")).isEqualTo(new NpmRoute.DistTags(UTIL));
        assertThat(NpmRoute.parse("-/package/@acme/util/dist-tags/Next")).isEqualTo(new NpmRoute.DistTag(UTIL, "next"));
        assertThat(NpmRoute.parse("-/v1/search")).isEqualTo(new NpmRoute.Unsupported("search"));
        assertThat(NpmRoute.parse("-/v1/login")).isEqualTo(new NpmRoute.Unsupported("login"));
        assertThat(NpmRoute.parse("-/org/acme/user")).isEqualTo(new NpmRoute.Unsupported("org"));
        assertThat(NpmRoute.parse("-/%00")).isEqualTo(new NpmRoute.Unsupported("this"));
        assertThat(NpmRoute.parse("lodash/-rev/1-a")).isEqualTo(new NpmRoute.Revision(lodash()));
        assertThat(NpmRoute.parse("lodash/-rev/undefined")).isEqualTo(new NpmRoute.Revision(lodash()));
        assertThat(NpmRoute.parse("lodash/-/lodash-1.0.0.tgz/-rev/2-b"))
                .isEqualTo(new NpmRoute.TarballRevision(lodash(), "lodash-1.0.0.tgz"));
        assertThat(NpmRoute.parse("-/package/lodash/collaborators")).isEqualTo(new NpmRoute.Unsupported("access"));
    }

    @ParameterizedTest
    @CsvSource({
        "@acme",
        "@acme%252futil",
        "lodash/-/evil.zip",
        "lodash/1.0",
        "lodash/1.0.0/extra",
        "Lodash",
        "-/package/lodash/dist-tags/1.2.3",
        "lodash/-rev/",
        "lodash/-rev/1-a/extra",
        "lodash/-/evil.zip/-rev/1-a",
        "lodash/-/lodash-1.0.0.tgz/-rev/"
    })
    void refusesWhatIsNoValidRoute(String path) {
        assertThatThrownBy(() -> NpmRoute.parse(path)).isInstanceOf(RefusedException.class);
    }

    @ParameterizedTest
    @CsvSource({"1.0.0", "0.0.1-alpha.1", "1.2.3+build.5", "10.20.30-rc.1+meta"})
    void acceptsSemVer(String version) {
        assertThat(new NpmVersion(version).toString()).isEqualTo(version);
    }

    @ParameterizedTest
    @CsvSource({
        "01.0.0",
        "1.0",
        "v1.0.0",
        "1.0.0-",
        "1.0.0+",
        "latest",
        "1.0.0-a..b",
        "1.0.0-.a",
        "1.0.0+b.",
        "1.0.0-a_b",
        "1.0.0+x+y"
    })
    void refusesWhatIsNotSemVer(String version) {
        assertThatThrownBy(() -> new NpmVersion(version)).isInstanceOf(NpmException.class);
    }

    @Test
    void refusesOverlongPaths() {
        assertThatThrownBy(() -> NpmRoute.parse("a".repeat(800))).isInstanceOf(NpmException.class);
    }

    @FuzzTest(maxDuration = "10s")
    void neverThrowsAnythingButARefusal(String path) {
        if (path == null) {
            return;
        }
        try {
            NpmRoute.parse(path);
        } catch (RefusedException e) {
            assertThat(e.status()).isBetween(400, 499);
        }
    }

    @Test
    void defaultsItsLimit() {
        assertThat(new NpmProperties(null, null).maxTarballSizeOrDefault()).isEqualTo(256L * 1024 * 1024);
        assertThat(new NpmProperties(true, 5L).maxTarballSizeOrDefault()).isEqualTo(5);
    }

    private static NpmPackageName lodash() {
        return NpmPackageName.fromPath("lodash");
    }
}
