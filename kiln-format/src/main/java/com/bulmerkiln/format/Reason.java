/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

/**
 * Why the registry refuses a request, and the one HTTP status every format answers it with (FMT-1).
 *
 * <ul>
 *   <li>{@link #BLOCKED} is {@code 403}, never {@code 404}: containerd mirrors and some npm and
 *       Maven setups fall through to the next registry on a {@code 404}, which may serve the
 *       blocked artifact.
 *   <li>{@link #OVER_QUOTA} is {@code 403}: a {@code 5xx} makes docker and buildkit retry
 *       endlessly, and {@code 507} reads as a server fault.
 *   <li>Nothing is a {@code 5xx}, which npm and docker retry.
 * </ul>
 */
public enum Reason {
    INVALID(400),
    UNAUTHORIZED(401),
    DENIED(403),
    BLOCKED(403),
    OVER_QUOTA(403),
    NOT_FOUND(404),
    UNSUPPORTED(405),
    CONFLICT(409),
    TOO_LARGE(413),
    RATE_LIMITED(429);

    private final int status;

    Reason(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
