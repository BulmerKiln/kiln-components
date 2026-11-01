/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignInClientDirectoryTest {

    /** AUTH-18: a directory built at boot finds its own connectors and nothing else. */
    @Test
    void findsOnlyItsOwnConnectors() {
        SignInClientDirectory directory = SignInClientDirectory.of(List.of(client("github"), client("corp")));

        assertThat(directory.find("github")).map(SignInClient::getClientId).contains("github-client");
        assertThat(directory.find("other")).isEmpty();
        assertThat(directory.find("../github")).isEmpty();
    }

    /** AUTH-18: two clients for one connector ID would make the callback ambiguous. */
    @Test
    void refusesAConnectorConfiguredTwice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SignInClientDirectory.of(List.of(client("github"), client("github"))))
                .withMessage("Connector github is configured twice");
    }

    /** AUTH-19: the resolved secret never appears in a client's string form. */
    @Test
    void neverShowsTheSecret() {
        assertThat(client("github").toString()).doesNotContain("s3cret").contains("(redacted)");
    }

    static SignInClient client(String id) {
        return new SignInClient(
                new ConnectorId(id),
                id + "-client",
                "s3cret",
                List.of("read:user"),
                URI.create("https://idp.example/authorize"),
                URI.create("https://idp.example/token"),
                null,
                null,
                null);
    }
}
