/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

class SafeXmlTest {

    @TempDir
    Path temp;

    @Test
    void parsesMavenMetadata() {
        Document doc = SafeXml.parse(bytes("""
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata><groupId>com.acme</groupId><artifactId>lib</artifactId>
                  <versioning><latest>1.0</latest><versions><version>1.0</version></versions></versioning></metadata>
                """));
        assertThat(doc.getElementsByTagName("latest").item(0).getTextContent()).isEqualTo("1.0");
    }

    @Test
    void parsesAPomWithNamespaces() {
        Document doc = SafeXml.parse(bytes("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion></project>
                """));
        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("http://maven.apache.org/POM/4.0.0");
    }

    @Test
    void refusesExternalEntitiesThatReadFiles() throws Exception {
        Path secret = Files.writeString(temp.resolve("secret"), "TOP-SECRET");
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE metadata [<!ENTITY xxe SYSTEM "%s">]>
                <metadata><groupId>&xxe;</groupId></metadata>
                """.formatted(secret.toUri());
        assertThatThrownBy(() -> SafeXml.parse(bytes(xxe)))
                .isInstanceOf(SafeXml.InvalidXmlException.class)
                .hasMessageNotContaining("TOP-SECRET");
    }

    @Test
    void refusesExternalDtdsAndParameterEntities() {
        assertThatThrownBy(() -> SafeXml.parse(bytes("<!DOCTYPE m SYSTEM \"http://127.0.0.1:1/evil.dtd\"><m/>")))
                .isInstanceOf(SafeXml.InvalidXmlException.class);
        assertThatThrownBy(() ->
                        SafeXml.parse(bytes("<!DOCTYPE m [<!ENTITY % p SYSTEM \"http://127.0.0.1:1/x\"> %p;]><m/>")))
                .isInstanceOf(SafeXml.InvalidXmlException.class);
    }

    @Test
    void refusesEntityExpansionBombsQuickly() {
        StringBuilder bomb = new StringBuilder("<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\">");
        for (int i = 1; i <= 9; i++) {
            bomb.append("<!ENTITY lol")
                    .append(i)
                    .append(" \"")
                    .append(("&lol" + (i == 1 ? "" : i - 1) + ";").repeat(10))
                    .append("\">");
        }
        bomb.append("]><lolz>&lol9;</lolz>");
        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> assertThatThrownBy(() -> SafeXml.parse(bytes(bomb.toString())))
                        .isInstanceOf(SafeXml.InvalidXmlException.class));
    }

    @Test
    void ignoresXInclude() {
        Document doc = SafeXml.parse(
                bytes(
                        "<m xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"file:///etc/passwd\" parse=\"text\"/></m>"));
        assertThat(doc.getDocumentElement().getTextContent()).isEmpty();
    }

    @Test
    void refusesMalformedXml() {
        for (String xml : new String[] {"", "<m>", "<m></n>", "not xml", "<m/><m/>"}) {
            assertThatThrownBy(() -> SafeXml.parse(bytes(xml))).as(xml).isInstanceOf(SafeXml.InvalidXmlException.class);
        }
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    @FuzzTest
    void parseOnlyFailsWithInvalidXml(FuzzedDataProvider data) {
        try {
            SafeXml.parse(data.consumeRemainingAsBytes());
        } catch (SafeXml.InvalidXmlException expected) {
            // The only failure untrusted XML may cause.
        }
    }
}
