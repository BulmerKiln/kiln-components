/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2.autoconfigure;

import com.bulmerkiln.auth.oauth2.OAuth2Connector;
import com.bulmerkiln.auth.oauth2.constant.OAuth2Preset;
import java.net.URI;
import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth2 connectors declared in boot configuration, validated by {@link OAuth2Connector#from}. */
@ConfigurationProperties("kiln.auth.oauth2")
@Value
public final class OAuth2Properties {

    private final @Nullable List<Connector> connectors;

    public List<Connector> connectorList() {
        return connectors == null ? List.of() : connectors;
    }

    /**
     * One connector.
     */
    @Value
    public static final class Connector {

        private final @Nullable String id;

        private final @Nullable String displayName;

        private final @Nullable OAuth2Preset preset;

        /** The namespace subjects live in, such as {@code https://github.com}; it keeps two IdPs' user IDs apart. */
        private final @Nullable String issuer;

        /** The IdP's endpoints; a preset fills any left out. */
        private final @Nullable Urls urls;

        /** Where the user-info response holds each value; a preset fills any left out. */
        private final @Nullable Paths paths;

        private final @Nullable List<String> scopes;

        private final @Nullable String clientId;

        /** A secret reference such as {@code env:KILN_GITHUB_SECRET}, never the secret. */
        private final @Nullable String clientSecret;

        private final @Nullable List<String> domains;
    }

    /**
     * The IdP's endpoints.
     */
    @Value
    public static final class Urls {

        private final @Nullable URI authorization;

        private final @Nullable URI token;

        private final @Nullable URI userInfo;

        /** Allows {@code http} endpoints, for a local IdP only. */
        private final boolean allowInsecure;
    }

    /**
     * Where the user-info response holds each value, as dotted paths such as {@code data.user.id}.
     */
    @Value
    public static final class Paths {

        private final @Nullable String userId;

        /** Used only together with {@code emailVerified}. */
        private final @Nullable String email;

        private final @Nullable String emailVerified;

        private final @Nullable String groups;
    }
}
