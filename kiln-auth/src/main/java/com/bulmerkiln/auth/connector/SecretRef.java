/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Where a secret, such as a client secret, is read from. Secrets are never written inline in
 * configuration (AUTH-14), so configuration holds only one of these:
 *
 * <ul>
 *   <li>{@code env:KILN_GITHUB_CLIENT_SECRET}, an environment variable
 *   <li>{@code file:/var/run/secrets/kiln/github}, a mounted file, such as a Kubernetes Secret
 * </ul>
 */
public sealed interface SecretRef {

    /** Conventional environment variable names only. */
    Pattern ENV_NAME = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");

    record Env(String name) implements SecretRef {

        public Env {
            if (!ENV_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid environment variable name");
            }
        }

        @Override
        public String toString() {
            return "env:" + name;
        }
    }

    record File(Path path) implements SecretRef {

        public File {
            if (!path.isAbsolute() || !path.normalize().equals(path)) {
                throw new IllegalArgumentException("A secret file must be an absolute, normalized path");
            }
        }

        @Override
        public String toString() {
            return "file:" + path;
        }
    }

    /**
     * Parses a reference. Anything else, including what looks like an inline secret, is refused
     * with a message that never repeats the value.
     */
    static SecretRef parse(String value) {
        if (value.startsWith("env:")) {
            return new Env(value.substring(4));
        }
        if (value.startsWith("file:") && value.length() > 5 && value.indexOf('\0') < 0) {
            return new File(Path.of(value.substring(5)));
        }
        throw new IllegalArgumentException("A secret must be a reference (env:NAME or file:/path), not a value");
    }
}
