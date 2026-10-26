/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectorContractTest {

    private static final ConnectorId GITHUB = new ConnectorId("github");

    @ParameterizedTest
    @ValueSource(strings = {"github", "acme-sso", "a", "okta2"})
    void acceptsConnectorIds(String id) {
        assertThat(new ConnectorId(id)).hasToString(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GitHub", "-x", "x-", "a--b", "a/b", "../x", "a b", "a%2f", "\u0430cme", "a\u0000"})
    void refusesConnectorIdsUnsafeInPathsAndCookies(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConnectorId(id));
    }

    @Test
    void identitiesKeepIssuerSubjectAndGroups() {
        Set<String> groups = new HashSet<>(Set.of("acme/platform"));
        ExternalIdentity identity = new ExternalIdentity(GITHUB, "https://github.com", "123", null, groups);
        groups.add("acme/admins");

        assertThat(identity.groups()).containsExactly("acme/platform");
        assertThatThrownBy(() -> identity.groups().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void identitiesRefuseUnboundedOrBlankValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExternalIdentity(GITHUB, " ", "1", null, Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExternalIdentity(GITHUB, "iss", "", null, Set.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExternalIdentity(GITHUB, "iss", "s".repeat(256), null, Set.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExternalIdentity(GITHUB, "iss", "1", null, Set.of(" ")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExternalIdentity(GITHUB, "iss", "1", null, Set.of("g".repeat(256))));
        Set<String> many = new HashSet<>();
        for (int i = 0; i <= ExternalIdentity.MAX_GROUPS; i++) {
            many.add("g" + i);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new ExternalIdentity(GITHUB, "iss", "1", null, many));
    }

    @Test
    void parsesSecretReferences() {
        assertThat(SecretRef.parse("env:KILN_GITHUB_SECRET")).isEqualTo(new SecretRef.Env("KILN_GITHUB_SECRET"));
        assertThat(SecretRef.parse("file:/var/run/secrets/kiln/github"))
                .isEqualTo(new SecretRef.File(Path.of("/var/run/secrets/kiln/github")))
                .hasToString("file:/var/run/secrets/kiln/github");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "hunter2",
                "gho_abcdefghijklmnopqrstuvwxyz0123456789",
                "env:",
                "env:lower",
                "env:KILN SECRET",
                "env:KILN;rm",
                "file:",
                "file:relative/path",
                "file:/var/run/../../etc/shadow",
                "file:/var/run/./secret",
                "file:/var/run/secret\u0000.txt",
                "vault:secret/kiln",
                "ENV:KILN"
            })
    void refusesInlineSecretsAndUnsafeReferencesWithoutEchoingThem(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SecretRef.parse(value))
                .satisfies(e -> {
                    if (value.length() > 8) {
                        assertThat(e.getMessage()).doesNotContain(value);
                    }
                });
    }

    @Test
    void endpointsMustBeHttps() {
        URI issuer = URI.create("https://idp.example.com/realms/acme");
        assertThat(Endpoints.requireSecure(issuer, false, "issuer")).isEqualTo(issuer);
        assertThat(Endpoints.requireSecure(URI.create("http://dex:5556/dex"), true, "issuer"))
                .isNotNull();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Endpoints.requireSecure(URI.create("http://idp.example.com"), false, "issuer"))
                .withMessageContaining("issuer");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ftp://idp.example.com",
                "javascript:alert(1)",
                "file:///etc/passwd",
                "https://user:pass@idp.example.com",
                "https://idp.example.com/#frag",
                "/relative",
                "https:///nohost"
            })
    void refusesUnsafeEndpoints(String uri) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Endpoints.requireSecure(URI.create(uri), true, "endpoint"));
    }

    @Test
    void normalizesDomains() {
        assertThat(Domains.normalize(List.of("Acme.COM", "eu.acme.com", "xn--bcher-kva.example")))
                .containsExactlyInAnyOrder("acme.com", "eu.acme.com", "xn--bcher-kva.example");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "acme",
                "acme.com.",
                ".acme.com",
                "*.acme.com",
                "acme..com",
                "-acme.com",
                "acme.com/x",
                "user@acme.com",
                "acme.com:443",
                "b\u00fccher.example",
                "acme.123",
                "a_b.com"
            })
    void refusesDomainsThatAreNotPlainHostnames(String domain) {
        assertThatIllegalArgumentException().isThrownBy(() -> Domains.normalize(List.of(domain)));
    }

    @FuzzTest
    void secretReferenceParsingOnlyFailsWithIllegalArgument(FuzzedDataProvider data) {
        try {
            SecretRef.parse(data.consumeRemainingAsString());
        } catch (IllegalArgumentException expected) {
            // The only failure a configured reference may cause.
        }
    }
}
