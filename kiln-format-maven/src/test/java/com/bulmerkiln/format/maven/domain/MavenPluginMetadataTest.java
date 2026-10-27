/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MavenPluginMetadataTest {

    /** FMT-MVN-17: the plugins of group metadata, as Maven's deploy writes them. */
    @Test
    void readsThePluginsOfGroupMetadata() {
        assertThat(MavenPluginMetadata.parse(bytes("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <metadata>
                          <plugins>
                            <plugin>
                              <name>Acme Plugin</name><prefix>acme</prefix><artifactId>acme-maven-plugin</artifactId>
                            </plugin>
                            <plugin><prefix>lint</prefix><artifactId>lint-maven-plugin</artifactId></plugin>
                          </plugins>
                        </metadata>
                        """)))
                .containsExactly(
                        new MavenPlugin("acme-maven-plugin", "acme", "Acme Plugin"),
                        new MavenPlugin("lint-maven-plugin", "lint", null));
    }

    /** FMT-MVN-17: artifact metadata lists no plugins, and the rest of an upload is ignored. */
    @Test
    void findsNoPluginsInArtifactMetadata() {
        assertThat(MavenPluginMetadata.parse(bytes("""
                        <metadata><groupId>org.acme</groupId><artifactId>core</artifactId>
                          <versioning><versions><version>1.0</version></versions></versioning></metadata>
                        """))).isEmpty();
    }

    /** FMT-MVN-17: an entry Kiln can't trust refuses the whole upload. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "not xml",
                "<project/>",
                "<metadata><plugins><plugin><prefix>acme</prefix></plugin></plugins></metadata>",
                "<metadata><plugins><plugin><artifactId>a</artifactId></plugin></plugins></metadata>",
                "<metadata><plugins><plugin><prefix>a b</prefix><artifactId>a</artifactId></plugin></plugins>"
                        + "</metadata>",
                "<metadata><plugins><plugin><prefix>a</prefix><artifactId>../a</artifactId></plugin></plugins>"
                        + "</metadata>",
                "<!DOCTYPE metadata [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><metadata>&x;</metadata>"
            })
    void refusesWhatItCantTrust(String xml) {
        assertThatThrownBy(() -> MavenPluginMetadata.parse(bytes(xml)))
                .isInstanceOfSatisfying(
                        RefusedException.class,
                        e -> assertThat(e.refusal().reason()).isEqualTo(Reason.INVALID));
    }

    /** FMT-MVN-17: names are bounded and carry no control characters; plugins are bounded too. */
    @Test
    void boundsWhatItKeeps() {
        String longName = "n".repeat(MavenPlugin.MAX_NAME + 1);
        assertThatThrownBy(() -> new MavenPlugin("a", "a", longName)).isInstanceOf(RefusedException.class);
        assertThatThrownBy(() -> new MavenPlugin("a", "a", "bell\u0007")).isInstanceOf(RefusedException.class);
        String many = "<plugin><prefix>p</prefix><artifactId>a</artifactId></plugin>"
                .repeat(MavenPluginMetadata.MAX_PLUGINS + 1);
        assertThatThrownBy(
                        () -> MavenPluginMetadata.parse(bytes("<metadata><plugins>" + many + "</plugins></metadata>")))
                .isInstanceOf(RefusedException.class);
    }

    /** FMT-MVN-17: whatever arrives, parsing either succeeds or refuses; nothing else escapes. */
    @FuzzTest
    void parsingOnlyFailsWithARefusal(FuzzedDataProvider data) {
        try {
            MavenPluginMetadata.parse(data.consumeRemainingAsBytes());
        } catch (RefusedException expected) {
            assertThat(expected.refusal().reason()).isEqualTo(Reason.INVALID);
        }
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
