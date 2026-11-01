/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.domain.IdpResponse;
import com.bulmerkiln.auth.connector.domain.SecretRef;
import com.bulmerkiln.auth.connector.util.DomainUtils;
import com.bulmerkiln.auth.connector.util.EndpointUtils;
import com.bulmerkiln.auth.oauth2.autoconfigure.OAuth2Properties;
import com.bulmerkiln.auth.oauth2.constant.OAuth2Preset;
import com.bulmerkiln.auth.oauth2.domain.OAuth2JsonPath;
import com.bulmerkiln.auth.oauth2.service.OAuth2UserMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** A validated OAuth2 connector. */
@Value
public final class OAuth2Connector implements IdentityConnector {

    private final ConnectorId id;

    private final String displayName;

    private final OAuth2Preset preset;

    private final String issuer;

    private final Urls urls;

    private final UserPaths paths;

    private final List<String> scopes;

    private final String clientId;

    private final SecretRef clientSecret;

    private final Set<String> domains;

    static final String GITHUB_TEAMS = "teams";

    /** The IdP's endpoints, all https unless the connector allows otherwise (AUTH-OAUTH2-5). */
    @Value
    public static final class Urls {

        private final URI authorization;

        private final URI token;

        private final URI userInfo;
    }

    /** Where the user-info response holds each value (AUTH-OAUTH2-3). */
    @Value
    public static final class UserPaths {

        private final OAuth2JsonPath userId;

        private final @Nullable OAuth2JsonPath email;

        private final @Nullable OAuth2JsonPath emailVerified;

        private final @Nullable OAuth2JsonPath groups;
    }

    public OAuth2Connector(
            ConnectorId id,
            String displayName,
            OAuth2Preset preset,
            String issuer,
            Urls urls,
            UserPaths paths,
            List<String> scopes,
            String clientId,
            SecretRef clientSecret,
            Set<String> domains) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(urls, "urls");
        Objects.requireNonNull(paths, "paths");
        this.scopes = List.copyOf(scopes);
        this.domains = Set.copyOf(domains);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("Connector " + id + ": issuer must not be blank");
        }
        this.id = id;
        this.displayName = displayName;
        this.preset = preset;
        this.issuer = issuer;
        this.urls = urls;
        this.paths = paths;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Validates a configured connector; preset values fill whatever isn't set. */
    public static OAuth2Connector from(OAuth2Properties.Connector config) {
        ConnectorId id = new ConnectorId(required(config.getId(), "id", "?"));
        OAuth2Preset preset = Objects.requireNonNullElse(config.getPreset(), OAuth2Preset.GENERIC);
        return new OAuth2Connector(
                id,
                Objects.requireNonNullElse(config.getDisplayName(), id.getValue()),
                preset,
                required(or(config.getIssuer(), preset.issuer()), "issuer", id),
                urls(config.getUrls(), preset, id),
                paths(config.getPaths(), preset, id),
                Objects.requireNonNullElse(config.getScopes(), preset.scopes()),
                required(config.getClientId(), "client-id", id),
                SecretRef.parse(required(config.getClientSecret(), "client-secret", id)),
                DomainUtils.normalize(config.getDomains() == null ? List.of() : config.getDomains()));
    }

    private static Urls urls(OAuth2Properties.@Nullable Urls config, OAuth2Preset preset, ConnectorId id) {
        OAuth2Properties.Urls urls = config == null ? new OAuth2Properties.Urls(null, null, null, false) : config;
        boolean insecure = urls.isAllowInsecure();
        return new Urls(
                endpoint(or(urls.getAuthorization(), preset.authorizationUri()), "urls.authorization", id, insecure),
                endpoint(or(urls.getToken(), preset.tokenUri()), "urls.token", id, insecure),
                endpoint(or(urls.getUserInfo(), preset.userInfoUri()), "urls.user-info", id, insecure));
    }

    private static UserPaths paths(OAuth2Properties.@Nullable Paths config, OAuth2Preset preset, ConnectorId id) {
        OAuth2Properties.Paths paths = config == null ? new OAuth2Properties.Paths(null, null, null, null) : config;
        return new UserPaths(
                OAuth2JsonPath.parse(required(or(paths.getUserId(), preset.userIdPath()), "paths.user-id", id)),
                path(paths.getEmail()),
                path(paths.getEmailVerified()),
                path(paths.getGroups()));
    }

    @Override
    public String type() {
        return "oauth2";
    }

    @Override
    public boolean providesGroups() {
        return paths.getGroups() != null || preset == OAuth2Preset.GITHUB;
    }

    /** GitHub's teams come from {@code /user/teams}, next to the user-info endpoint. */
    @Override
    public Map<String, URI> lookups() {
        return preset == OAuth2Preset.GITHUB
                ? Map.of(GITHUB_TEAMS, urls.getUserInfo().resolve("user/teams?per_page=100"))
                : Map.of();
    }

    /** Maps the user-info response and lookups; see {@link OAuth2UserMapper}. */
    @Override
    public ExternalIdentity identify(IdpResponse response) {
        Object teams = response.getLookups().get(GITHUB_TEAMS);
        return OAuth2UserMapper.map(
                this,
                response.getClaims(),
                preset == OAuth2Preset.GITHUB && teams instanceof List<?> list
                        ? OAuth2UserMapper.gitHubTeams(list)
                        : Set.of());
    }

    private static URI endpoint(@Nullable URI uri, String setting, ConnectorId id, boolean insecure) {
        return EndpointUtils.requireSecure(required(uri, setting, id), insecure, "Connector " + id + " " + setting);
    }

    private static @Nullable OAuth2JsonPath path(@Nullable String path) {
        return path == null ? null : OAuth2JsonPath.parse(path);
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
