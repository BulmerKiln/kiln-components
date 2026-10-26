/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.connector.ExternalIdentity;
import com.bulmerkiln.auth.connector.InvalidIdentityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OidcClaimsMapperTest {

    private static final String ISSUER = "https://sso.acme.com/realms/acme";

    private final OidcConnector connector =
            OidcConnector.from(OidcConnectorTest.config("sso", OidcPreset.KEYCLOAK, ISSUER, null));

    @Test
    void mapsAVerifiedIdentity() {
        ExternalIdentity identity = OidcClaimsMapper.map(
                connector,
                claims(Map.of(
                        "email", "ada@acme.com", "email_verified", true, "groups", List.of("platform", "admins"))));

        assertThat(identity.connector()).isEqualTo(connector.id());
        assertThat(identity.issuer()).isEqualTo(ISSUER);
        assertThat(identity.subject()).isEqualTo("f2c1-ada");
        assertThat(identity.email()).isEqualTo("ada@acme.com");
        assertThat(identity.groups()).containsExactlyInAnyOrder("platform", "admins");
    }

    /** The issuer must match byte for byte (OIDC Core 3.1.3.7), or a token from another IdP or realm could pass. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://sso.acme.com/realms/acme/",
                "https://SSO.acme.com/realms/acme",
                "https://sso.acme.com/realms/Acme",
                "https://sso.acme.com/realms/evil",
                "https://sso.acme.com:443/realms/acme",
                "http://sso.acme.com/realms/acme",
                "https://sso.acme.com/realms/acme/../acme",
                "https://sso.acme.com.evil.com/realms/acme",
                ""
            })
    void refusesAnyOtherIssuer(String issuer) {
        Map<String, Object> claims = claims(Map.of());
        claims.put("iss", issuer);
        assertThatThrownBy(() -> OidcClaimsMapper.map(connector, claims))
                .isInstanceOf(InvalidIdentityException.class)
                .hasMessageNotContaining(issuer.isEmpty() ? "\0" : issuer);
    }

    @Test
    void refusesANonStringIssuer() {
        Map<String, Object> claims = claims(Map.of());
        claims.put("iss", List.of(ISSUER));
        assertThatThrownBy(() -> OidcClaimsMapper.map(connector, claims)).isInstanceOf(InvalidIdentityException.class);
    }

    @Test
    void refusesMissingOrUnusableSubjects() {
        for (Object subject : new Object[] {null, "", "  ", "s".repeat(256), 42, List.of("a")}) {
            Map<String, Object> claims = claims(Map.of());
            claims.put("sub", subject);
            assertThatThrownBy(() -> OidcClaimsMapper.map(connector, claims))
                    .isInstanceOf(InvalidIdentityException.class);
        }
    }

    @Test
    void ignoresUnverifiedEmails() {
        // An attacker who can set an unverified email at the IdP must not be matched to its owner.
        for (Object verified : new Object[] {null, false, "true", 1, "TRUE"}) {
            Map<String, Object> claims = claims(Map.of("email", "ceo@acme.com"));
            claims.put("email_verified", verified);
            assertThat(OidcClaimsMapper.map(connector, claims).email()).isNull();
        }
    }

    @Test
    void ignoresOverlongEmails() {
        String email = "a".repeat(320) + "@acme.com";
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of("email", email, "email_verified", true)))
                        .email())
                .isNull();
    }

    @Test
    void readsGroupsLeniently() {
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of("groups", "solo")))
                        .groups())
                .containsExactly("solo");
        List<Object> mixed = new ArrayList<>(List.of("a", 42, Map.of("x", 1), "", " ", "b", "a"));
        mixed.add(null);
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of("groups", mixed)))
                        .groups())
                .containsExactlyInAnyOrder("a", "b");
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of("groups", Map.of("admins", true))))
                        .groups())
                .isEmpty();
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of())).groups()).isEmpty();
    }

    @Test
    void readsOnlyTheConfiguredGroupsClaim() {
        OidcConnector gitlab = OidcConnector.from(OidcConnectorTest.config("gitlab", OidcPreset.GITLAB, ISSUER, null));
        Map<String, Object> claims =
                claims(Map.of("groups", List.of("injected"), "groups_direct", List.of("acme/dev")));
        assertThat(OidcClaimsMapper.map(gitlab, claims).groups()).containsExactly("acme/dev");
    }

    @Test
    void refusesUnboundedGroups() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= ExternalIdentity.MAX_GROUPS; i++) {
            many.add("g" + i);
        }
        assertThatThrownBy(() -> OidcClaimsMapper.map(connector, claims(Map.of("groups", many))))
                .isInstanceOf(InvalidIdentityException.class);
        assertThatThrownBy(() -> OidcClaimsMapper.map(connector, claims(Map.of("groups", List.of("g".repeat(256))))))
                .isInstanceOf(InvalidIdentityException.class);
    }

    @Test
    void googleWorkspaceAcceptsOnlyItsHostedDomains() {
        OidcConnector google = OidcConnector.from(OidcConnectorTest.config(
                "google", OidcPreset.GOOGLE_WORKSPACE, "https://accounts.google.com", List.of("acme.com")));
        Map<String, Object> claims = new HashMap<>(Map.of("iss", "https://accounts.google.com", "sub", "1089"));

        assertThatThrownBy(() -> OidcClaimsMapper.map(google, claims)).isInstanceOf(InvalidIdentityException.class);
        claims.put("hd", "evil.com");
        assertThatThrownBy(() -> OidcClaimsMapper.map(google, claims)).isInstanceOf(InvalidIdentityException.class);
        claims.put("hd", "ACME.com");
        assertThatThrownBy(() -> OidcClaimsMapper.map(google, claims)).isInstanceOf(InvalidIdentityException.class);
        claims.put("hd", "acme.com");
        assertThat(OidcClaimsMapper.map(google, claims).subject()).isEqualTo("1089");
    }

    @Test
    void otherPresetsIgnoreTheHostedDomain() {
        assertThat(OidcClaimsMapper.map(connector, claims(Map.of("hd", "evil.com")))
                        .subject())
                .isEqualTo("f2c1-ada");
    }

    private static Map<String, Object> claims(Map<String, Object> extra) {
        Map<String, Object> claims = new HashMap<>(Map.of("iss", ISSUER, "sub", "f2c1-ada"));
        claims.putAll(extra);
        return claims;
    }
}
