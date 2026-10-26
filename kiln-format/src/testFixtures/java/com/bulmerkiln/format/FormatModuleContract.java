/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every {@link FormatModule} must do. Each format's test extends this and provides
 * {@link #module()}.
 */
public abstract class FormatModuleContract {

    /** Everything a format's error body must survive intact: quotes, escapes, markup, separators. */
    protected static final String HOSTILE =
            "name \"x\\\\y\" </script><script>alert(1)</script> {\"k\":1}] \n\r\t \u0000 \u2028 \u00e9 \ud83d\ude00";

    private static final String CHALLENGE = "Bearer realm=\"https://pkg.example/token\",service=\"pkg.example\"";

    protected abstract FormatModule module();

    @Test
    void registersOnItsOwn() {
        assertThatCode(() -> new FormatRegistry(List.of(module()))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void rendersEveryReasonWithItsFixedStatus(Reason reason) {
        FormatResponse response = FormatResponse.refusal(module(), refusal(reason, "refused for testing"));

        assertThat(response.status()).isEqualTo(reason.status()).isBetween(400, 499);
        assertThat(response.contentType()).isNotBlank();
        assertThat(response.body()).isNotBlank();
        assertThat(response.headers()).containsEntry("X-Content-Type-Options", "nosniff");
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void carriesTheMessageSoClientsSeeTheReason(Reason reason) {
        assertThat(messagesIn(FormatResponse.refusal(
                        module(), refusal(reason, "organization acme over quota: 499.8 / 500 GiB"))))
                .contains("organization acme over quota: 499.8 / 500 GiB");
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void escapesHostileMessages(Reason reason) {
        FormatResponse response = FormatResponse.refusal(module(), refusal(reason, HOSTILE));
        assertThat(messagesIn(response))
                .as("the body must round-trip the message exactly")
                .contains(HOSTILE);
    }

    @Test
    void sendsTheChallengeOnUnauthorized() {
        FormatResponse response =
                FormatResponse.refusal(module(), Refusal.unauthorized("authentication required", CHALLENGE));
        assertThat(response.headers()).containsEntry("WWW-Authenticate", CHALLENGE);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = "UNAUTHORIZED", mode = EnumSource.Mode.EXCLUDE)
    void sendsNoChallengeOtherwise(Reason reason) {
        assertThat(FormatResponse.refusal(module(), refusal(reason, "no")).headers())
                .doesNotContainKey("WWW-Authenticate");
    }

    @Test
    void acceptsAnyCodeHintWithoutFailing() {
        for (String code : List.of("BLOB_UNKNOWN", "E404", "NOT_A_REAL_CODE")) {
            assertThatCode(() -> FormatResponse.refusal(
                            module(), Refusal.of(Reason.NOT_FOUND, "missing").withCode(code)))
                    .doesNotThrowAnyException();
        }
    }

    /**
     * The messages a client would read from the body: every string in a JSON body, or the whole
     * body otherwise. JSON must parse strictly.
     */
    protected List<String> messagesIn(FormatResponse response) {
        String type = response.contentType();
        if (!type.startsWith("application/json") && !type.contains("+json")) {
            return List.of(response.body());
        }
        JsonNode root = JsonMapper.builder().build().readTree(response.body());
        List<String> strings = new ArrayList<>();
        collect(root, strings);
        return strings;
    }

    private static void collect(JsonNode node, List<String> strings) {
        if (node.isString()) {
            strings.add(node.asString());
        }
        for (JsonNode child : node.values()) {
            collect(child, strings);
        }
    }

    private static Refusal refusal(Reason reason, String message) {
        return reason == Reason.UNAUTHORIZED ? Refusal.unauthorized(message, CHALLENGE) : Refusal.of(reason, message);
    }
}
