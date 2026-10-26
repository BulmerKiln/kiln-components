/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FormatRulesTest {

    static FormatModule module(String id, String segment, Map<String, String> headers) {
        return new FormatModule() {
            @Override
            public ArtifactFormat format() {
                return new ArtifactFormat(id);
            }

            @Override
            public String pathSegment() {
                return segment;
            }

            @Override
            public RefusalBody render(Refusal refusal) {
                return new RefusalBody(
                        "application/json", FormatJson.write(Map.of("error", refusal.message())), headers);
            }
        };
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void noReasonIsAServerErrorAndBlocksAreNeverNotFound(Reason reason) {
        assertThat(reason.status()).isBetween(400, 499);
        if (reason == Reason.BLOCKED || reason == Reason.OVER_QUOTA) {
            assertThat(reason.status()).isEqualTo(403);
        }
    }

    @Test
    void formatsCannotOverrideTheSecurityHeaders() {
        FormatModule sneaky = module(
                "npm",
                "npm",
                Map.of(
                        "x-content-type-options",
                        "sniff",
                        "WWW-Authenticate",
                        "Basic realm=\"evil\"",
                        "X-Npm-Notice",
                        "use the web app"));
        FormatResponse response = FormatResponse.refusal(sneaky, Refusal.of(Reason.BLOCKED, "blocked"));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.headers())
                .containsEntry("X-Content-Type-Options", "nosniff")
                .containsEntry("X-Npm-Notice", "use the web app")
                .doesNotContainKey("WWW-Authenticate")
                .hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer realm=\"x\"\r\nSet-Cookie: a=b", "Basic\nx", "Basic\u0000", "Basic\u007f"})
    void refusesHeaderInjectionInChallenges(String challenge) {
        assertThatIllegalArgumentException().isThrownBy(() -> Refusal.unauthorized("auth", challenge));
    }

    @Test
    void refusesHeaderInjectionFromFormats() {
        FormatModule injecting = module("npm", "npm", Map.of("X-Notice", "a\r\nSet-Cookie: x=y"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FormatResponse.refusal(injecting, Refusal.of(Reason.INVALID, "bad")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FormatResponse(400, "text/plain\r\nX: y", "b", Map.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FormatResponse(400, "text/plain", "b", Map.of("Bad Name", "v")));
    }

    @Test
    void aChallengeBelongsOnlyToUnauthorized() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Refusal(Reason.UNAUTHORIZED, "auth", null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Refusal(Reason.DENIED, "no", null, "Basic realm=\"x\""));
    }

    @Test
    void boundsMessagesAndCodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> Refusal.of(Reason.INVALID, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> Refusal.of(Reason.INVALID, "m".repeat(1025)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Refusal.of(Reason.INVALID, "m").withCode("lower"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Refusal.of(Reason.INVALID, "m").withCode("A\nB"));
        assertThat(Refusal.of(Reason.NOT_FOUND, "m").withCode("BLOB_UNKNOWN").code())
                .isEqualTo("BLOB_UNKNOWN");
    }

    @Test
    void refusedExceptionsCarryTheirStatus() {
        RefusedException e = new RefusedException(Refusal.of(Reason.TOO_LARGE, "too big"));
        assertThat(e.status()).isEqualTo(413);
        assertThat(e).hasMessage("too big").hasNoCause();
    }

    @Test
    void registryRoutesBySegmentAndFormat() {
        FormatRegistry registry =
                new FormatRegistry(List.of(module("oci", "v2", Map.of()), module("npm", "npm", Map.of())));
        assertThat(registry.forSegment("v2")).map(FormatModule::format).contains(new ArtifactFormat("oci"));
        assertThat(registry.get(new ArtifactFormat("npm"))).isPresent();
        assertThat(registry.forSegment("api")).isEmpty();
        assertThat(registry.formats()).hasSize(2);
    }

    @Test
    void refusesConflictingFormats() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new FormatRegistry(List.of(module("npm", "npm", Map.of()), module("npm", "maven", Map.of()))))
                .withMessageContaining("format npm");
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new FormatRegistry(List.of(module("npm", "npm", Map.of()), module("yarn", "npm", Map.of()))))
                .withMessageContaining("/npm/");
    }

    /** A route outside the reserved list would collide with an organization's slug. */
    @ParameterizedTest
    @ValueSource(strings = {"pypi", "acme", "V2", "", "v2/x", "..", "-npm"})
    void refusesRoutesThatAreNotReservedSlugs(String segment) {
        assertThatIllegalStateException()
                .isThrownBy(() -> new FormatRegistry(List.of(module("pypi", segment, Map.of()))));
    }

    @Test
    void artifactRefsHaveNoBlankParts() {
        ArtifactFormat npm = new ArtifactFormat("npm");
        assertThat(new ArtifactRef(npm, "@acme", "ui", "1.0.0").version()).isEqualTo("1.0.0");
        assertThat(new ArtifactRef(npm, "@acme", "ui", null).version()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactRef(npm, " ", "ui", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactRef(npm, "@acme", "", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactRef(npm, "@acme", "ui", ""));
    }

    @Test
    void autoConfigurationSkipsDisabledFormats() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FormatAutoConfiguration.class))
                .withBean("oci", FormatModule.class, () -> module("oci", "v2", Map.of()))
                .withBean("npm", FormatModule.class, () -> module("npm", "npm", Map.of()))
                .withPropertyValues("kiln.format.npm.enabled=false")
                .run(context -> assertThat(context.getBean(FormatRegistry.class).formats())
                        .extracting(FormatModule::format)
                        .containsExactly(new ArtifactFormat("oci")));
    }

    @Test
    void autoConfigurationStartsWithNoFormats() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FormatAutoConfiguration.class))
                .run(context -> assertThat(context.getBean(FormatRegistry.class).formats())
                        .isEmpty());
    }

    @Test
    void accessOutcomesHaveFixedRefusals() {
        Supplier<String> challenge = () -> "Bearer realm=\"https://pkg.example/token\"";
        assertThat(Access.ALLOW.toRefusal("x", challenge)).isEmpty();
        assertThat(Access.AUTHENTICATE.toRefusal("sign in", challenge)).get().satisfies(r -> {
            assertThat(r.reason()).isEqualTo(Reason.UNAUTHORIZED);
            assertThat(r.challenge()).startsWith("Bearer");
        });
        assertThat(Access.DENY.toRefusal("no", challenge))
                .get()
                .extracting(Refusal::reason)
                .isEqualTo(Reason.DENIED);
        assertThat(Access.HIDE.toRefusal("not found", challenge))
                .get()
                .extracting(Refusal::reason)
                .isEqualTo(Reason.NOT_FOUND);
    }

    @Test
    void theChallengeIsBuiltOnlyWhenNeeded() {
        Supplier<String> exploding = () -> {
            throw new AssertionError("not needed");
        };
        for (Access access : new Access[] {Access.ALLOW, Access.DENY, Access.HIDE}) {
            access.toRefusal("x", exploding);
        }
    }

    @Test
    void eventsReachEveryListenerInOrder() {
        List<String> seen = new ArrayList<>();
        ArtifactEventListener a = listener("a", seen);
        ArtifactEventListener b = listener("b", seen);
        ArtifactRef ref = new ArtifactRef(new ArtifactFormat("npm"), "@acme", "ui", "1.0.0");

        ArtifactEvents events = new ArtifactEvents(List.of(a, b));
        events.pushed(ref, 10);
        events.pulled(ref, 0);

        assertThat(seen).containsExactly("a pushed 10", "b pushed 10", "a pulled 0", "b pulled 0");
        assertThatIllegalArgumentException().isThrownBy(() -> events.pulled(ref, -1));
    }

    @Test
    void aListenerThatMustNotLoseAnEventFailsTheRequest() {
        ArtifactEventListener audit = new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {
                throw new IllegalStateException("audit log unavailable");
            }

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {}
        };
        ArtifactEvents events = new ArtifactEvents(List.of(audit));
        assertThatIllegalStateException()
                .isThrownBy(() -> events.pushed(new ArtifactRef(new ArtifactFormat("oci"), "acme", "app", "1"), 1));
    }

    @Test
    void thereAreNoEventsWithoutAListener() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactEvents(List.of()));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FormatAutoConfiguration.class))
                .run(context -> assertThatThrownBy(() -> context.getBean(ArtifactEvents.class))
                        .hasRootCauseMessage("ArtifactEvents needs at least one listener"));
        List<String> seen = new ArrayList<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FormatAutoConfiguration.class))
                .withBean(ArtifactEventListener.class, () -> listener("metrics", seen))
                .run(context -> assertThat(context).hasSingleBean(ArtifactEvents.class));
    }

    private static ArtifactEventListener listener(String name, List<String> seen) {
        return new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {
                seen.add(name + " pushed " + bytes);
            }

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {
                seen.add(name + " pulled " + bytes);
            }
        };
    }
}
