/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.bulmerkiln.auth.connector.domain.SignInClient;
import com.bulmerkiln.auth.oidc.OidcConnector;
import com.bulmerkiln.auth.oidc.autoconfigure.OidcProperties;
import com.bulmerkiln.auth.oidc.domain.OidcEndpoints;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OidcSignInClientDirectoryTest {

    private static final URI ISSUER = URI.create("https://sso.acme.com");
    private static final OidcEndpoints ENDPOINTS = new OidcEndpoints(
            URI.create("https://sso.acme.com/auth"),
            URI.create("https://sso.acme.com/token"),
            URI.create("https://sso.acme.com/userinfo"),
            URI.create("https://sso.acme.com/keys"));

    @TempDir
    Path dir;

    /** AUTH-OIDC-7: endpoints come from discovery, fetched once on first use and then kept. */
    @Test
    void discoversOnFirstUseAndKeepsTheResult() throws Exception {
        List<URI> fetched = new ArrayList<>();
        var directory = new OidcSignInClientDirectory(List.of(connector("sso")), issuer -> {
            fetched.add(issuer);
            return ENDPOINTS;
        });

        assertThat(fetched).isEmpty();
        SignInClient client = directory.find("sso").orElseThrow();
        directory.find("sso");

        assertThat(fetched).containsExactly(ISSUER);
        assertThat(client.getClientId()).isEqualTo("kiln");
        assertThat(client.getClientSecret()).isEqualTo("s3cret");
        assertThat(client.getScopes()).contains("openid");
        assertThat(client.getAuthorizationUri()).isEqualTo(ENDPOINTS.getAuthorization());
        assertThat(client.getTokenUri()).isEqualTo(ENDPOINTS.getToken());
        assertThat(client.getUserInfoUri()).isEqualTo(ENDPOINTS.getUserInfo());
        assertThat(client.getJwkSetUri()).isEqualTo(ENDPOINTS.getJwkSet());
        assertThat(client.getIssuer()).isEqualTo(ISSUER);
    }

    /** AUTH-OIDC-7: a failed fetch isn't kept, so the next sign-in tries again. */
    @Test
    void triesDiscoveryAgainAfterAFailure() throws Exception {
        List<URI> fetched = new ArrayList<>();
        Function<URI, OidcEndpoints> downThenUp = issuer -> {
            fetched.add(issuer);
            if (fetched.size() == 1) {
                throw new IllegalStateException("IdP unreachable");
            }
            return ENDPOINTS;
        };
        var directory = new OidcSignInClientDirectory(List.of(connector("sso")), downThenUp);

        assertThatIllegalStateException().isThrownBy(() -> directory.find("sso"));
        assertThat(directory.find("sso")).isPresent();
        assertThat(fetched).hasSize(2);
    }

    /** AUTH-OIDC-7: a connector ID from the request path that isn't configured fetches nothing. */
    @Test
    void findsNothingForAnUnknownConnector() throws Exception {
        var directory = new OidcSignInClientDirectory(List.of(connector("sso")), issuer -> {
            throw new AssertionError("no discovery for an unknown connector");
        });

        assertThat(directory.find("other")).isEmpty();
        assertThat(directory.find("")).isEmpty();
    }

    /** AUTH-OIDC-6: an unreadable secret stops the boot, before any sign-in. */
    @Test
    void refusesAnUnreadableSecretAtBoot() {
        OidcConnector connector = OidcConnector.from(new OidcProperties.Connector(
                "sso", null, null, ISSUER, "kiln", "env:KILN_DEFINITELY_NOT_SET_VAR_XYZ", null, null, null, false));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OidcSignInClientDirectory(List.of(connector), issuer -> ENDPOINTS))
                .withMessageStartingWith("Connector sso: client-secret: ");
    }

    private OidcConnector connector(String id) throws Exception {
        Path secret = dir.resolve(id);
        Files.writeString(secret, "s3cret");
        return OidcConnector.from(new OidcProperties.Connector(
                id, null, null, ISSUER, "kiln", "file:" + secret, null, null, null, false));
    }
}
