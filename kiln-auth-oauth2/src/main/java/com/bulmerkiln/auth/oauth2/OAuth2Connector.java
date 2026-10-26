/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.ConnectorId;
import com.bulmerkiln.auth.connector.Domains;
import com.bulmerkiln.auth.connector.Endpoints;
import com.bulmerkiln.auth.connector.ExternalIdentity;
import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.IdpResponse;
import com.bulmerkiln.auth.connector.SecretRef;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A validated OAuth2 connector. */
public record OAuth2Connector(
        ConnectorId id,
        String displayName,
        OAuth2Preset preset,
        String issuer,
        Urls urls,
        UserPaths paths,
        List<String> scopes,
        String clientId,
        SecretRef clientSecret,
        Set<String> domains)
        implements IdentityConnector {

    static final String GITHUB_TEAMS = "teams";

    /** The IdP's endpoints, all https unless the connector allows otherwise (AUTH-OAUTH2-5). */
    public record Urls(URI authorization, URI token, URI userInfo) {}

    /** Where the user-info response holds each value (AUTH-OAUTH2-3). */
    public record UserPaths(
            JsonPath userId,
            @Nullable JsonPath email,
            @Nullable JsonPath emailVerified,
            @Nullable JsonPath groups) {}

    public OAuth2Connector {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(urls, "urls");
        Objects.requireNonNull(paths, "paths");
        scopes = List.copyOf(scopes);
        domains = Set.copyOf(domains);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("Connector " + id + ": issuer must not be blank");
        }
    }

    /** Validates a configured connector; preset values fill whatever isn't set. */
    public static OAuth2Connector from(OAuth2Properties.Connector config) {
        ConnectorId id = new ConnectorId(required(config.id(), "id", "?"));
        OAuth2Preset preset = Objects.requireNonNullElse(config.preset(), OAuth2Preset.GENERIC);
        return new OAuth2Connector(
                id,
                Objects.requireNonNullElse(config.displayName(), id.value()),
                preset,
                required(or(config.issuer(), preset.issuer()), "issuer", id),
                urls(config.urls(), preset, id),
                paths(config.paths(), preset, id),
                Objects.requireNonNullElse(config.scopes(), preset.scopes()),
                required(config.clientId(), "client-id", id),
                SecretRef.parse(required(config.clientSecret(), "client-secret", id)),
                Domains.normalize(config.domains() == null ? List.of() : config.domains()));
    }

    private static Urls urls(OAuth2Properties.@Nullable Urls config, OAuth2Preset preset, ConnectorId id) {
        OAuth2Properties.Urls urls = config == null ? new OAuth2Properties.Urls(null, null, null, false) : config;
        boolean insecure = urls.allowInsecure();
        return new Urls(
                endpoint(or(urls.authorization(), preset.authorizationUri()), "urls.authorization", id, insecure),
                endpoint(or(urls.token(), preset.tokenUri()), "urls.token", id, insecure),
                endpoint(or(urls.userInfo(), preset.userInfoUri()), "urls.user-info", id, insecure));
    }

    private static UserPaths paths(OAuth2Properties.@Nullable Paths config, OAuth2Preset preset, ConnectorId id) {
        OAuth2Properties.Paths paths = config == null ? new OAuth2Properties.Paths(null, null, null, null) : config;
        return new UserPaths(
                JsonPath.parse(required(or(paths.userId(), preset.userIdPath()), "paths.user-id", id)),
                path(paths.email()),
                path(paths.emailVerified()),
                path(paths.groups()));
    }

    @Override
    public String type() {
        return "oauth2";
    }

    @Override
    public boolean providesGroups() {
        return paths.groups() != null || preset == OAuth2Preset.GITHUB;
    }

    /** GitHub's teams come from {@code /user/teams}, next to the user-info endpoint. */
    @Override
    public Map<String, URI> lookups() {
        return preset == OAuth2Preset.GITHUB
                ? Map.of(GITHUB_TEAMS, urls.userInfo().resolve("user/teams?per_page=100"))
                : Map.of();
    }

    /** Maps the user-info response and lookups; see {@link OAuth2UserMapper}. */
    @Override
    public ExternalIdentity identify(IdpResponse response) {
        Object teams = response.lookups().get(GITHUB_TEAMS);
        return OAuth2UserMapper.map(
                this,
                response.claims(),
                preset == OAuth2Preset.GITHUB && teams instanceof List<?> list
                        ? OAuth2UserMapper.gitHubTeams(list)
                        : Set.of());
    }

    private static URI endpoint(@Nullable URI uri, String setting, ConnectorId id, boolean insecure) {
        return Endpoints.requireSecure(required(uri, setting, id), insecure, "Connector " + id + " " + setting);
    }

    private static @Nullable JsonPath path(@Nullable String path) {
        return path == null ? null : JsonPath.parse(path);
    }

    private static <T> @Nullable T or(@Nullable T configured, @Nullable T preset) {
        return configured != null ? configured : preset;
    }

    private static <T> T required(@Nullable T value, String setting, Object id) {
        if (value == null) {
            throw new IllegalArgumentException("Connector " + id + ": " + setting + " is required");
        }
        return value;
    }
}
