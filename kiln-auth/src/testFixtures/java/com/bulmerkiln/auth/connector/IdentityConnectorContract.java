/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * What every {@link IdentityConnector} must do, whatever its protocol. A connector's test extends
 * this and builds protocol-shaped responses; the assertions are the same for all of them.
 */
public abstract class IdentityConnectorContract {

    /** A connector that reports groups. */
    protected abstract IdentityConnector connector();

    /**
     * A connector of the same protocol configured without groups, or null if the protocol always
     * reports them.
     */
    protected abstract IdentityConnector connectorWithoutGroups();

    /**
     * A verified response for {@code connector()}.
     *
     * @param subject the raw user ID to put where the protocol puts it; may be any JSON value
     * @param email put in the response as verified, unless null
     * @param groups put where the connector reads groups from, unless null, which leaves them out
     */
    protected abstract IdpResponse response(Object subject, String email, List<String> groups);

    /** A response whose email is present but not marked verified. */
    protected abstract IdpResponse responseWithUnverifiedEmail(String subject, String email);

    /** The group names the connector reports for these IdP groups; most report them unchanged. */
    protected List<String> expectedGroups(List<String> idpGroups) {
        return idpGroups;
    }

    @Test
    void describesItself() {
        IdentityConnector connector = connector();
        assertThat(connector.type()).isNotBlank().isEqualTo(connector.type().toLowerCase(Locale.ROOT));
        assertThat(connector.displayName()).isNotBlank();
        assertThat(connector.domains()).allSatisfy(d -> assertThat(d).isEqualTo(d.toLowerCase(Locale.ROOT)));
        assertThat(connector.providesGroups()).isTrue();
        assertThatCode(() -> new StaticConnectorRegistry(List.of(connector))).doesNotThrowAnyException();
    }

    @Test
    void identifiesAPersonWithTheirGroups() {
        ExternalIdentity identity =
                connector().identify(response("user-1", "ada@acme.com", List.of("platform", "admins")));

        assertThat(identity.connector()).isEqualTo(connector().id());
        assertThat(identity.issuer()).isNotBlank();
        assertThat(identity.subject()).isEqualTo("user-1");
        assertThat(identity.email()).isEqualTo("ada@acme.com");
        assertThat(identity.groups()).containsAll(expectedGroups(List.of("platform", "admins")));
    }

    @Test
    void identifiesTheSamePersonTheSameWayEveryTime() {
        IdpResponse response = response("user-1", "ada@acme.com", List.of("platform"));
        assertThat(connector().identify(response)).isEqualTo(connector().identify(response));
    }

    /** No groups from the IdP means no groups, and so no roles: least privilege. */
    @Test
    void reportsNoGroupsWhenTheIdpSendsNone() {
        assertThat(connector().identify(response("user-1", null, null)).groups())
                .isEmpty();
        assertThat(connector().identify(response("user-1", null, List.of())).groups())
                .isEmpty();
    }

    @Test
    void reportsNoGroupsWhenConfiguredWithoutThem() {
        IdentityConnector withoutGroups = connectorWithoutGroups();
        if (withoutGroups == null) {
            return;
        }
        assertThat(withoutGroups.providesGroups()).isFalse();
        assertThat(withoutGroups
                        .identify(response("user-1", null, List.of("admins")))
                        .groups())
                .isEmpty();
    }

    @Test
    void ignoresAnUnverifiedEmail() {
        assertThat(connector()
                        .identify(responseWithUnverifiedEmail("user-1", "ceo@acme.com"))
                        .email())
                .isNull();
    }

    @Test
    void refusesResponsesWithoutAUsableSubject() {
        List<Object> subjects =
                new ArrayList<>(List.of("", "   ", "s".repeat(256), "user\n1", 1.5, true, List.of("user-1")));
        subjects.add(null);
        for (Object subject : subjects) {
            assertThatThrownBy(() -> connector().identify(response(subject, null, null)))
                    .as(String.valueOf(subject))
                    .isInstanceOf(InvalidIdentityException.class);
        }
    }

    @Test
    void neverEchoesWhatItRefuses() {
        String hostile = "s".repeat(250) + "TOP-SECRET-VALUE";
        assertThatThrownBy(() -> connector().identify(response(hostile, null, null)))
                .isInstanceOf(InvalidIdentityException.class)
                .hasMessageNotContaining("TOP-SECRET-VALUE");
    }

    @Test
    void dropsGroupNamesUnsafeToStoreOrLog() {
        ExternalIdentity identity =
                connector().identify(response("user-1", null, List.of("ok", "", " ", "bad\nINFO forged", "nul\u0000")));
        assertThat(identity.groups())
                .containsAll(expectedGroups(List.of("ok")))
                .noneMatch(g -> g.isBlank() || g.chars().anyMatch(Character::isISOControl));
    }

    @Test
    void refusesUnboundedGroups() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= ExternalIdentity.MAX_GROUPS; i++) {
            many.add("g" + i);
        }
        assertThatThrownBy(() -> connector().identify(response("user-1", null, many)))
                .isInstanceOf(InvalidIdentityException.class);
        assertThatThrownBy(() -> connector().identify(response("user-1", null, List.of("g".repeat(256)))))
                .isInstanceOf(InvalidIdentityException.class);
    }
}
