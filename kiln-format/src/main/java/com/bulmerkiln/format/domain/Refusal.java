/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.domain;

import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.dto.FormatResponse;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A refused request, independent of format. Each format renders it in its own error body.
 */
@Value
public final class Refusal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * For the client, written by Kiln, such as {@code organization acme over quota: 499.8 / 500 GiB};
     * formats must escape it, since it can quote names a client sent.
     */
    private final String message;

    /**
     * A format-specific error code hint, such as OCI's {@code BLOB_UNKNOWN}; a format uses it only
     * when it agrees with {@code reason}.
     */
    private final @Nullable String code;

    /**
     * The {@code WWW-Authenticate} value, required for {@link Reason#UNAUTHORIZED} and produced by the auth subsystem.
     */
    private final @Nullable String challenge;

    public static final int MAX_MESSAGE_LENGTH = 1024;

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public Refusal(Reason reason, String message, @Nullable String code, @Nullable String challenge) {
        Objects.requireNonNull(reason, "reason");
        if (message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("A refusal message is 1 to " + MAX_MESSAGE_LENGTH + " characters");
        }
        if (code != null && !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid error code");
        }
        if ((reason == Reason.UNAUTHORIZED) != (challenge != null)) {
            throw new IllegalArgumentException("A challenge is required for, and only for, UNAUTHORIZED");
        }
        if (challenge != null) {
            FormatResponse.requireHeaderValue(challenge);
        }
        this.reason = reason;
        this.message = message;
        this.code = code;
        this.challenge = challenge;
    }

    public static Refusal of(Reason reason, String message) {
        return new Refusal(reason, message, null, null);
    }

    public static Refusal unauthorized(String message, String challenge) {
        return new Refusal(Reason.UNAUTHORIZED, message, null, challenge);
    }

    /** The same refusal with a format-specific code hint. */
    public Refusal withCode(String code) {
        return new Refusal(reason, message, code, challenge);
    }
}
