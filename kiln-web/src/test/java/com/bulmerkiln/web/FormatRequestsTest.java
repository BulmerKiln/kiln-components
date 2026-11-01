/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.dto.RefusalResponse;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.web.service.AnonymousPrincipalResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FormatRequestsTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    private static final Principal MEMBER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());

    /** A format whose errors are plain text, so the tests can see the refusal went through it. */
    private static final FormatModule TEXT = new FormatModule() {
        @Override
        public ArtifactFormat format() {
            return new ArtifactFormat("text");
        }

        @Override
        public String pathSegment() {
            return "v2";
        }

        @Override
        public RefusalResponse render(Refusal refusal) {
            return RefusalResponse.of("text/plain", "refused: " + refusal.getMessage());
        }
    };

    /** Takes the first path segment as the organization, so the tests see the path change. */
    private static final OrganizationResolver BY_FIRST_SEGMENT = path -> path.startsWith("acme/")
            ? Optional.of(new OrganizationResolver.Resolution(ACME, path.substring("acme/".length())))
            : Optional.empty();

    private final FormatRequests requests =
            new FormatRequests(BY_FIRST_SEGMENT, request -> MEMBER, "https://pkg.example.com");
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /** WEB-1: the handler runs for the organization and caller, with the path inside it. */
    @Test
    void bindsTheOrganizationAndCallerForTheHandler() throws Exception {
        List<Object> seen = new ArrayList<>();

        requests.scoped(request, response, TEXT, "acme/team/app", path -> {
            seen.add(path);
            seen.add(OrganizationContext.current());
            seen.add(CurrentPrincipal.get());
        });

        assertThat(seen).containsExactly("team/app", ACME, MEMBER);
        assertThat(OrganizationContext.isBound()).isFalse();
    }

    /** WEB-1: an organization that doesn't resolve looks exactly like a missing artifact. */
    @Test
    void answersAnUnknownOrganizationAsNotFound() throws Exception {
        List<String> ran = new ArrayList<>();

        requests.scoped(request, response, TEXT, "globex/app", ran::add);

        assertThat(ran).isEmpty();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEqualTo("refused: Not found");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    /** WEB-2: a refusal is answered in the format's shape, replacing anything the handler set. */
    @Test
    void answersARefusalInTheFormatsShape() throws Exception {
        requests.scoped(request, response, TEXT, "acme/app", path -> {
            response.setHeader("Docker-Content-Digest", "sha256:leaked");
            throw new RefusedException(Refusal.unauthorized("Sign in", "Bearer realm=\"kiln\""));
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getContentAsString()).isEqualTo("refused: Sign in");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"kiln\"");
        assertThat(response.getHeader("Docker-Content-Digest")).isNull();
    }

    /** WEB-2: once part of an answer is sent, a refusal can't follow it, so the request fails. */
    @Test
    void cutsAStreamThatWasAlreadyCommitted() {
        assertThatThrownBy(() -> requests.scoped(request, response, TEXT, "acme/app", path -> {
                    response.getOutputStream().write(1);
                    response.flushBuffer();
                    throw new RefusedException(Refusal.of(Reason.DENIED, "No"));
                }))
                .isInstanceOf(RefusedException.class);
    }

    /** WEB-3: a caller the resolver refuses, such as one with an expired token, is answered too. */
    @Test
    void answersARefusedCaller() throws Exception {
        FormatRequests refusing = new FormatRequests(
                BY_FIRST_SEGMENT,
                request -> {
                    throw new RefusedException(Refusal.of(Reason.DENIED, "Token revoked"));
                },
                "https://pkg.example.com");

        refusing.scoped(request, response, TEXT, "acme/app", path -> {});

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo("refused: Token revoked");
    }

    /** WEB-1: a route without an artifact binds the caller and no organization. */
    @Test
    void runsUnscopedRoutesWithTheCallerOnly() throws Exception {
        List<Object> seen = new ArrayList<>();

        requests.unscoped(request, response, TEXT, () -> {
            seen.add(OrganizationContext.isBound());
            seen.add(CurrentPrincipal.get());
        });

        assertThat(seen).containsExactly(false, MEMBER);
    }

    @Test
    void answersARefusalOnAnUnscopedRoute() throws Exception {
        requests.unscoped(request, response, TEXT, () -> {
            throw new RefusedException(Refusal.of(Reason.RATE_LIMITED, "Slow down"));
        });

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).isEqualTo("refused: Slow down");
    }

    /** Anything that isn't a refusal is a fault, not an answer, and isn't turned into one. */
    @Test
    void letsFaultsThrough() {
        assertThatThrownBy(() -> requests.scoped(request, response, TEXT, "acme/app", path -> {
                    throw new IllegalStateException("database down");
                }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writesAnAnswerWithItsLength() throws Exception {
        FormatRequests.write(response, new FormatResponse(200, "application/json", "{\"é\":1}", Map.of("A", "b")));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("A")).isEqualTo("b");
        assertThat(response.getContentLength()).isEqualTo(8);
    }

    /** WEB-5: absolute URLs come from configuration. */
    @Test
    void buildsUrlsFromTheConfiguredBase() {
        assertThat(requests.baseUrl()).isEqualTo("https://pkg.example.com");
    }

    @Test
    void theDefaultCallerIsAnonymous() {
        assertThat(new AnonymousPrincipalResolver().resolve(request)).isEqualTo(new Principal.Anonymous());
    }
}
