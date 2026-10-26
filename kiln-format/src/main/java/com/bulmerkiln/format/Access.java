/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The outcome of an {@link ArtifactAccess} check. Each refusal has its own status so a format
 * never decides between them itself.
 */
public enum Access {
    ALLOW,
    /** {@code 401} with the format's challenge: docker and npm sign in only when challenged. */
    AUTHENTICATE,
    /** {@code 403}: the caller may know the artifact exists, but may not do this. */
    DENY,
    /** {@code 404}: the caller may not even learn the artifact exists. */
    HIDE;

    /**
     * The refusal to answer with, or empty when allowed.
     *
     * @param challenge the format's {@code WWW-Authenticate} value, asked for only on {@code AUTHENTICATE}
     */
    public Optional<Refusal> toRefusal(String message, Supplier<String> challenge) {
        return switch (this) {
            case ALLOW -> Optional.empty();
            case AUTHENTICATE -> Optional.of(Refusal.unauthorized(message, challenge.get()));
            case DENY -> Optional.of(Refusal.of(Reason.DENIED, message));
            case HIDE -> Optional.of(Refusal.of(Reason.NOT_FOUND, message));
        };
    }
}
