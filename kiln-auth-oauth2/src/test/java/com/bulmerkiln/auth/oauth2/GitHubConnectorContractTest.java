/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.IdentityConnectorContract;
import com.bulmerkiln.auth.connector.IdpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The GitHub preset: groups come from the {@code /user/teams} lookup as {@code organization/team}. */
class GitHubConnectorContractTest extends IdentityConnectorContract {

    private final OAuth2Connector github = OAuth2Connector.from(new OAuth2Properties.Connector(
            "github",
            null,
            OAuth2Preset.GITHUB,
            null,
            null,
            new OAuth2Properties.Paths(null, "email", "email_verified", null),
            null,
            "Iv1.abc",
            "env:KILN_GITHUB_SECRET",
            null));

    @Override
    protected IdentityConnector connector() {
        return github;
    }

    @Override
    protected IdentityConnector connectorWithoutGroups() {
        return null;
    }

    /**
     * Each group "organization/team" becomes a team in that organization; plain names become teams in
     * organization "acme".
     */
    @Override
    protected IdpResponse response(Object subject, String email, List<String> groups) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", subject);
        if (email != null) {
            user.put("email", email);
            user.put("email_verified", true);
        }
        if (groups == null) {
            return IdpResponse.of(user);
        }
        List<Map<String, Object>> teams = new ArrayList<>();
        for (String group : groups) {
            teams.add(Map.of("slug", group, "organization", Map.of("login", "acme")));
        }
        return new IdpResponse(user, Map.of(OAuth2Connector.GITHUB_TEAMS, teams));
    }

    @Override
    protected IdpResponse responseWithUnverifiedEmail(String subject, String email) {
        return IdpResponse.of(Map.of("id", subject, "email", email));
    }

    @Override
    protected List<String> expectedGroups(List<String> idpGroups) {
        return idpGroups.stream().map(g -> "acme/" + g).toList();
    }

    @Test
    void namesGroupsByOrganizationAndTeam() {
        assertThat(github.identify(response(583231, null, List.of("Platform"))).groups())
                .containsExactlyInAnyOrder("acme", "acme/platform");
    }

    @Test
    void looksUpTeamsNextToTheUserInfoEndpoint() {
        assertThat(github.lookups()).containsOnlyKeys("teams");
        assertThat(github.lookups().get("teams")).hasToString("https://api.github.com/user/teams?per_page=100");
    }

    @Test
    void aFailedTeamsLookupOnlyMeansFewerGroups() {
        assertThat(github.identify(IdpResponse.of(Map.of("id", 583231))).groups())
                .isEmpty();
        assertThat(github.identify(new IdpResponse(
                                Map.of("id", 583231), Map.of("teams", Map.of("message", "Bad credentials"))))
                        .groups())
                .isEmpty();
    }
}
