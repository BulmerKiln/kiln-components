/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MavenPathTest {

    @Test
    void parsesAReleaseJar() {
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.jar"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, null, "jar", null));
    }

    @Test
    void parsesClassifiersCompoundExtensionsAndChecksums() {
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0-sources.jar"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, "sources", "jar", null));
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.tar.gz"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, null, "tar.gz", null));
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.jar.asc"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, null, "jar.asc", null));
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.pom.sha512"))
                .isEqualTo(new MavenPath.ArtifactFile(
                        "com.acme", "lib", "1.0", null, null, "pom", ChecksumAlgorithm.SHA512));
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.module"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, null, "module", null));
    }

    @Test
    void parsesSnapshots() {
        MavenPath.ArtifactFile timestamped = (MavenPath.ArtifactFile)
                MavenPath.parse("com/acme/lib/1.0-SNAPSHOT/lib-1.0-20261025.101500-3-tests.jar");
        assertThat(timestamped.isSnapshot()).isTrue();
        assertThat(timestamped.buildVersion()).isEqualTo("1.0-20261025.101500-3");
        assertThat(timestamped.classifier()).isEqualTo("tests");

        MavenPath.ArtifactFile plain =
                (MavenPath.ArtifactFile) MavenPath.parse("com/acme/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT.jar");
        assertThat(plain.buildVersion()).isNull();
    }

    @Test
    void parsesMetadata() {
        assertThat(MavenPath.parse("com/acme/lib/maven-metadata.xml"))
                .isEqualTo(new MavenPath.Metadata("com.acme", "lib", null, null));
        assertThat(MavenPath.parse("com/acme/lib/maven-metadata.xml.sha1"))
                .isEqualTo(new MavenPath.Metadata("com.acme", "lib", null, ChecksumAlgorithm.SHA1));
        assertThat(MavenPath.parse("com/acme/lib/1.0-SNAPSHOT/maven-metadata.xml"))
                .isEqualTo(new MavenPath.Metadata("com.acme", "lib", "1.0-SNAPSHOT", null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "/com/acme/lib/1.0/lib-1.0.jar",
                "com/acme/lib/1.0/lib-1.0.jar/",
                "com//acme/lib/1.0/lib-1.0.jar",
                "com/../acme/lib/1.0/lib-1.0.jar",
                "com/acme/lib/1.0/../../../../etc/passwd",
                "com/./acme/lib/1.0/lib-1.0.jar",
                "com/acme/lib/1.0/.lib-1.0.jar",
                "com/acme/.git/config/x-config.jar",
                "com/acme/lib/1.0/lib-1.0.jar%00.pom",
                "com/acme/lib/1.0/lib-1.0.jar\u0000",
                "com\\acme/lib/1.0/lib-1.0.jar",
                "com/acme/lib/1.0/lib-1.0.jar?x=1",
                "com/acme/lib/1.0/lib-1.0.j%61r",
                "com/acme/lib/1.0/lib-1.1.jar",
                "com/acme/lib/1.0/other-1.0.jar",
                "com/acme/lib/1.0/lib-1.0",
                "com/acme/lib/1.0/lib-1.0-.jar",
                "com/acme/lib/1.0/lib-1.0-sources",
                "com/acme/lib/1.0/lib-1.0.",
                "com/acme/lib/1.0/lib-1.0.jar.",
                "com/acme/lib/1.0/lib-1.0..jar",
                "lib/1.0/lib-1.0.jar",
                "lib/maven-metadata.xml",
                "com/acme/lib/1.0-SNAPSHOT/lib-1.0-2026.jar",
                "com/acme/lib/1.0-SNAPSHOT/lib-1.0-20261025.101500.jar",
                "com/acme/lib/1.0-SNAPSHOT/lib-2.0-20261025.101500-3.jar",
                "com/acme/lib/1.0/lib-1.0-20261025.101500-3.jar",
                "com/acme/l\u00efb/1.0/l\u00efb-1.0.jar",
                "com/acme/lib/1.0/lib-1.0.md5"
            })
    void refusesInvalidAndTraversingPaths(String path) {
        assertThatThrownBy(() -> MavenPath.parse(path)).isInstanceOf(InvalidMavenPathException.class);
    }

    @Test
    void aReleaseFileNameMustMatchItsVersionExactly() {
        // "1.0" must not accept files of "1.0.1", which would let one version shadow another.
        assertThat(MavenPath.parse("com/acme/lib/1.0/lib-1.0.1.jar"))
                .isEqualTo(new MavenPath.ArtifactFile("com.acme", "lib", "1.0", null, null, "1.jar", null));
    }

    @Test
    void limitsPathLength() {
        assertThatThrownBy(() -> MavenPath.parse("a/".repeat(600) + "x")).isInstanceOf(InvalidMavenPathException.class);
    }

    @Test
    void errorsNeverEchoThePath() {
        String path = "com/acme/lib/1.0/../../secret-token-abc";
        assertThatThrownBy(() -> MavenPath.parse(path)).hasMessageNotContaining("secret-token-abc");
    }

    @FuzzTest
    void parsingOnlyFailsWithInvalidPath(FuzzedDataProvider data) {
        try {
            MavenPath parsed = MavenPath.parse(data.consumeRemainingAsString());
            assertThat(parsed.groupId()).doesNotContain("..", "/");
        } catch (InvalidMavenPathException expected) {
            // The only failure a path may cause.
        }
    }
}
