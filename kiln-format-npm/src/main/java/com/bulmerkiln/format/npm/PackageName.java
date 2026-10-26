/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import com.bulmerkiln.format.Reason;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * An npm package name, {@code name} or {@code @scope/name}, held to the rules npm applies to new
 * packages (validate-npm-package-name): at most 214 characters, lowercase, URL-safe, not starting
 * with {@code .} or {@code _}, and none of {@code ~'!()*}.
 */
public record PackageName(@Nullable String scope, String name) {

    public static final int MAX_LENGTH = 214;

    private static final Pattern PART = Pattern.compile("[a-z0-9-][a-z0-9._-]*");
    private static final Set<String> BLOCKED = Set.of("node_modules", "favicon.ico");

    public PackageName {
        if ((scope != null && !PART.matcher(scope).matches())
                || !PART.matcher(name).matches()
                || BLOCKED.contains(name)) {
            throw invalid();
        }
        if ((scope == null ? 0 : scope.length() + 2) + name.length() > MAX_LENGTH) {
            throw invalid();
        }
    }

    /** Parses {@code name} or {@code @scope/name}. */
    public static PackageName parse(String value) {
        if (value.startsWith("@")) {
            int slash = value.indexOf('/');
            if (slash < 0) {
                throw invalid();
            }
            return new PackageName(value.substring(1, slash), value.substring(slash + 1));
        }
        return new PackageName(null, value);
    }

    /**
     * Parses the raw (still percent-encoded) path segment a client sent. Scoped names arrive as
     * {@code @scope%2fname} from some clients and {@code @scope/name} from others; that slash is
     * the only escape accepted, so a double-encoded or otherwise escaped name can't slip past.
     */
    public static PackageName fromPath(String raw) {
        String decoded = raw.replace("%2f", "/").replace("%2F", "/");
        if (decoded.indexOf('%') >= 0) {
            throw invalid();
        }
        return parse(decoded);
    }

    /** The form for URLs, such as tarball links: {@code @scope%2fname}. */
    public String urlEncoded() {
        return scope == null ? name : "@" + scope + "%2f" + name;
    }

    @Override
    public String toString() {
        return scope == null ? name : "@" + scope + "/" + name;
    }

    private static NpmException invalid() {
        return new NpmException(Reason.INVALID, "Invalid package name");
    }
}
