/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.s3;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a download may redirect to storage. Some clients forward {@code Authorization}
 * to the redirect target, which S3 rejects and which would hand Kiln credentials to storage. The
 * allowlist below is the clients the client matrix shows don't (STO-S3-4).
 */
public record RedirectPolicy(RedirectMode mode) {

    /** The product token each client starts its {@code User-Agent} with, matched exactly. */
    static final Set<String> KNOWN_GOOD = Set.of(
            "docker",
            "containerd",
            "buildkit",
            "containers",
            "podman",
            "skopeo",
            "buildah",
            "crane",
            "go-containerregistry",
            "oras",
            "Helm",
            "npm",
            "Apache-Maven",
            "Gradle");

    public boolean shouldRedirect(@Nullable String userAgent) {
        return switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> userAgent != null && KNOWN_GOOD.contains(product(userAgent));
        };
    }

    /** The product name of the first token: {@code docker/27.1 go/1.22} gives {@code docker}. */
    private static String product(String userAgent) {
        int slash = userAgent.indexOf('/');
        return slash <= 0 ? "" : userAgent.substring(0, slash);
    }
}
