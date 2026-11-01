/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.connector.InvalidIdentityException;
import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import com.bulmerkiln.auth.oauth2.autoconfigure.OAuth2AutoConfiguration;
import com.bulmerkiln.auth.oauth2.autoconfigure.OAuth2Properties;
import com.bulmerkiln.auth.oauth2.constant.OAuth2Preset;
import com.bulmerkiln.auth.oauth2.domain.OAuth2JsonPath;
import com.bulmerkiln.auth.oauth2.service.OAuth2UserMapper;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OAuth2ConnectorTest {

    @TempDir
    Path dir;

    private final OAuth2Connector github = OAuth2Connector.from(github(null));

    @Test
    void theGitHubPresetFillsEverything() {
        assertThat(github.getIssuer()).isEqualTo("https://github.com");
        assertThat(github.getUrls().getToken()).hasToString("https://github.com/login/oauth/access_token");
        assertThat(github.getPaths().getUserId()).hasToString("id");
        assertThat(github.getScopes()).containsExactly("read:user", "user:email", "read:org");
        assertThat(github.type()).isEqualTo("oauth2");
    }

    @Test
    void gitHubEnterpriseOverridesTheUrls() {
        OAuth2Properties.Connector config = new OAuth2Properties.Connector(
                "ghe",
                null,
                OAuth2Preset.GITHUB,
                "https://github.acme.com",
                new OAuth2Properties.Urls(
                        URI.create("https://github.acme.com/login/oauth/authorize"),
                        URI.create("https://github.acme.com/login/oauth/access_token"),
                        URI.create("https://github.acme.com/api/v3/user"),
                        false),
                null,
                null,
                "kiln",
                "env:S",
                null);
        OAuth2Connector ghe = OAuth2Connector.from(config);
        assertThat(ghe.getIssuer()).isEqualTo("https://github.acme.com");
        assertThat(ghe.getUrls().getUserInfo()).hasToString("https://github.acme.com/api/v3/user");
    }

    @Test
    void aGenericConnectorMustConfigureItsEndpoints() {
        OAuth2Properties.Connector config = new OAuth2Properties.Connector(
                "bitbucket",
                null,
                null,
                "https://bitbucket.org",
                null,
                new OAuth2Properties.Paths("uuid", null, null, null),
                null,
                "kiln",
                "env:S",
                null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OAuth2Connector.from(config))
                .withMessageContaining("urls.authorization");
    }

    @Test
    void refusesInsecureEndpointsAndInlineSecrets() {
        OAuth2Properties.Connector http = new OAuth2Properties.Connector(
                "gh",
                null,
                OAuth2Preset.GITHUB,
                null,
                new OAuth2Properties.Urls(null, URI.create("http://github.com/token"), null, false),
                null,
                null,
                "kiln",
                "env:S",
                null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OAuth2Connector.from(http))
                .withMessageContaining("urls.token");
        OAuth2Properties.Connector inline = new OAuth2Properties.Connector(
                "gh", null, OAuth2Preset.GITHUB, null, null, null, null, "kiln", "abc123secret", null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OAuth2Connector.from(inline))
                .withMessageNotContaining("abc123secret");
    }

    @Test
    void mapsAGitHubUserByNumericId() {
        Map<String, Object> user = Map.of("id", 583231, "login", "octocat", "email", "octocat@github.com");
        ExternalIdentity identity = OAuth2UserMapper.map(github, user, Set.of());

        assertThat(identity.getIssuer()).isEqualTo("https://github.com");
        assertThat(identity.getSubject()).isEqualTo("583231");
        // GitHub's /user email isn't marked verified, so it isn't trusted.
        assertThat(identity.getEmail()).isNull();
    }

    @Test
    void acceptsLongAndBigIntegerIds() {
        assertThat(OAuth2UserMapper.map(github, Map.of("id", 9_000_000_000L), Set.of())
                        .getSubject())
                .isEqualTo("9000000000");
        assertThat(OAuth2UserMapper.map(
                                github, Map.of("id", new BigInteger("123456789012345678901234567890")), Set.of())
                        .getSubject())
                .isEqualTo("123456789012345678901234567890");
    }

    @Test
    void neverFallsBackToTheLogin() {
        // A login can be renamed and re-registered by someone else; only the immutable ID identifies.
        Map<String, Object> noId = Map.of("login", "octocat");
        assertThatThrownBy(() -> OAuth2UserMapper.map(github, noId, Set.of()))
                .isInstanceOf(InvalidIdentityException.class);
    }

    @Test
    void refusesUnusableIds() {
        for (Object id : new Object[] {1.5, 1.0, true, List.of(1), Map.of("id", 1), "", " ", "x".repeat(256)}) {
            Map<String, Object> user = new HashMap<>();
            user.put("id", id);
            assertThatThrownBy(() -> OAuth2UserMapper.map(github, user, Set.of()))
                    .as(String.valueOf(id))
                    .isInstanceOf(InvalidIdentityException.class);
        }
    }

    @Test
    void trustsAnEmailOnlyWhenTheIdpMarksItVerified() {
        OAuth2Connector generic = generic("email", "email_verified", "groups");
        assertThat(OAuth2UserMapper.map(
                                generic, Map.of("sub", "u1", "email", "a@acme.com", "email_verified", true), Set.of())
                        .getEmail())
                .isEqualTo("a@acme.com");
        for (Object verified : new Object[] {false, "true", 1}) {
            Map<String, Object> user = Map.of("sub", "u1", "email", "a@acme.com", "email_verified", verified);
            assertThat(OAuth2UserMapper.map(generic, user, Set.of()).getEmail()).isNull();
        }
        OAuth2Connector noVerifiedPath = generic("email", null, null);
        assertThat(OAuth2UserMapper.map(
                                noVerifiedPath,
                                Map.of("sub", "u1", "email", "a@acme.com", "email_verified", true),
                                Set.of())
                        .getEmail())
                .isNull();
    }

    @Test
    void readsNestedPathsAndGroups() {
        OAuth2Properties.Connector config = new OAuth2Properties.Connector(
                "corp",
                null,
                null,
                "https://corp.example",
                new OAuth2Properties.Urls(
                        URI.create("https://corp.example/auth"),
                        URI.create("https://corp.example/token"),
                        URI.create("https://corp.example/me"),
                        false),
                new OAuth2Properties.Paths("data.user.id", null, null, "data.groups"),
                List.of("profile"),
                "kiln",
                "env:S",
                null);
        OAuth2Connector corp = OAuth2Connector.from(config);
        Map<String, Object> user =
                Map.of("data", Map.of("user", Map.of("id", "u-7"), "groups", List.of("dev", 3, "", "ops")));

        ExternalIdentity identity = OAuth2UserMapper.map(corp, user, Set.of("extra"));
        assertThat(identity.getSubject()).isEqualTo("u-7");
        assertThat(identity.getGroups()).containsExactlyInAnyOrder("dev", "ops", "extra");
    }

    @Test
    void refusesUnboundedGroups() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= ExternalIdentity.MAX_GROUPS; i++) {
            many.add("g" + i);
        }
        assertThatThrownBy(() -> OAuth2UserMapper.map(github, Map.of("id", 1), many))
                .isInstanceOf(InvalidIdentityException.class);
        assertThatThrownBy(() -> OAuth2UserMapper.map(github, Map.of("id", 1), Set.of("g".repeat(256))))
                .isInstanceOf(InvalidIdentityException.class);
    }

    @Test
    void mapsGitHubTeamsToGroups() {
        List<Map<String, Object>> teams = List.of(
                Map.of("slug", "Platform", "organization", Map.of("login", "Acme")),
                Map.of("slug", "admins", "organization", Map.of("login", "acme")),
                Map.of("slug", "x"),
                Map.of("slug", 5, "organization", Map.of("login", "acme")),
                Map.of("slug", "", "organization", Map.of("login", "acme")),
                Map.of("slug", "y", "organization", "acme"));

        assertThat(OAuth2UserMapper.gitHubTeams(teams))
                .containsExactlyInAnyOrder("acme", "acme/platform", "acme/admins");
    }

    @Test
    void jsonPathsNameExactlyOneValue() {
        assertThat(OAuth2JsonPath.parse("a.b").read(Map.of("a", Map.of("b", 1))))
                .isEqualTo(1);
        assertThat(OAuth2JsonPath.parse("a.b").read(Map.of("a", List.of(Map.of("b", 1)))))
                .isNull();
        assertThat(OAuth2JsonPath.parse("a.b").read(Map.of("a", "b"))).isNull();
        assertThat(OAuth2JsonPath.parse("a").read(Map.of())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "a.", ".a", "a..b", "a[0]", "a.*", "$.a", "a b", "a.b.c.d.e.f.g.h.i"})
    void refusesAmbiguousJsonPaths(String path) {
        assertThatIllegalArgumentException().isThrownBy(() -> OAuth2JsonPath.parse(path));
    }

    @Test
    void autoConfiguresTheGitHubPreset() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OAuth2AutoConfiguration.class))
                .withPropertyValues(
                        "kiln.auth.oauth2.connectors[0].id=github",
                        "kiln.auth.oauth2.connectors[0].preset=github",
                        "kiln.auth.oauth2.connectors[0].client-id=Iv1.abc",
                        "kiln.auth.oauth2.connectors[0].client-secret=" + secretFile())
                .run(context -> assertThat(context.getBean(OAuth2Connectors.class)
                                .getConnectors()
                                .getFirst()
                                .getPreset())
                        .isEqualTo(OAuth2Preset.GITHUB));
    }

    @Test
    void bindsNestedUrlsAndPaths() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OAuth2AutoConfiguration.class))
                .withPropertyValues(
                        "kiln.auth.oauth2.connectors[0].id=corp",
                        "kiln.auth.oauth2.connectors[0].issuer=https://corp.example",
                        "kiln.auth.oauth2.connectors[0].urls.authorization=https://corp.example/auth",
                        "kiln.auth.oauth2.connectors[0].urls.token=https://corp.example/token",
                        "kiln.auth.oauth2.connectors[0].urls.user-info=https://corp.example/me",
                        "kiln.auth.oauth2.connectors[0].paths.user-id=data.id",
                        "kiln.auth.oauth2.connectors[0].paths.groups=data.groups",
                        "kiln.auth.oauth2.connectors[0].client-id=kiln",
                        "kiln.auth.oauth2.connectors[0].client-secret=" + secretFile())
                .run(context -> {
                    OAuth2Connector corp = context.getBean(OAuth2Connectors.class)
                            .getConnectors()
                            .getFirst();
                    assertThat(corp.getUrls().getUserInfo()).hasToString("https://corp.example/me");
                    assertThat(corp.getPaths().getUserId()).hasToString("data.id");
                    assertThat(corp.providesGroups()).isTrue();
                    SignInClient client = context.getBean(SignInClientDirectory.class)
                            .find("corp")
                            .orElseThrow();
                    assertThat(client.getAuthorizationUri()).hasToString("https://corp.example/auth");
                    assertThat(client.getUserInfoUri()).hasToString("https://corp.example/me");
                    assertThat(client.getClientSecret()).isEqualTo("s3cret");
                });
    }

    /** AUTH-19: a client secret that can't be read stops the boot, naming the connector. */
    @Test
    void stopsTheBootOnAnUnreadableSecret() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OAuth2AutoConfiguration.class))
                .withPropertyValues(
                        "kiln.auth.oauth2.connectors[0].id=github",
                        "kiln.auth.oauth2.connectors[0].preset=github",
                        "kiln.auth.oauth2.connectors[0].client-id=Iv1.abc",
                        "kiln.auth.oauth2.connectors[0].client-secret=env:KILN_DEFINITELY_NOT_SET_VAR_XYZ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Connector github: client-secret"));
    }

    private String secretFile() throws Exception {
        Path file = dir.resolve("secret");
        Files.writeString(file, "s3cret");
        return "file:" + file;
    }

    private static OAuth2Properties.Connector github(String id) {
        return new OAuth2Properties.Connector(
                id == null ? "github" : id,
                null,
                OAuth2Preset.GITHUB,
                null,
                null,
                null,
                null,
                "Iv1.abc",
                "env:KILN_GITHUB_SECRET",
                null);
    }

    private static OAuth2Connector generic(String emailPath, String verifiedPath, String groupsPath) {
        return OAuth2Connector.from(new OAuth2Properties.Connector(
                "corp",
                null,
                null,
                "https://corp.example",
                new OAuth2Properties.Urls(
                        URI.create("https://corp.example/auth"),
                        URI.create("https://corp.example/token"),
                        URI.create("https://corp.example/me"),
                        false),
                new OAuth2Properties.Paths("sub", emailPath, verifiedPath, groupsPath),
                List.of(),
                "kiln",
                "env:S",
                null));
    }

    @FuzzTest
    void jsonPathParsingOnlyFailsWithIllegalArgument(FuzzedDataProvider data) {
        try {
            OAuth2JsonPath path = OAuth2JsonPath.parse(data.consumeRemainingAsString());
            assertThat(OAuth2JsonPath.parse(path.toString())).isEqualTo(path);
        } catch (IllegalArgumentException expected) {
            // The only failure a configured path may cause.
        }
    }
}
