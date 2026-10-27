/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.NpmException;
import java.util.Locale;

/**
 * A registry route below {@code /npm/}. Scoped names arrive as {@code @scope/name} or {@code
 * @scope%2fname}; {@link NpmPackageName#fromPath} decides which forms are accepted (FMT-NPM-7).
 */
public sealed interface NpmRoute {

    record Ping() implements NpmRoute {}

    record Whoami() implements NpmRoute {}

    record Packument(NpmPackageName name) implements NpmRoute {}

    record Version(NpmPackageName name, NpmVersion version) implements NpmRoute {}

    record Tarball(NpmPackageName name, String file) implements NpmRoute {}

    record DistTags(NpmPackageName name) implements NpmRoute {}

    record DistTag(NpmPackageName name, String tag) implements NpmRoute {}

    /** An npm endpoint Kiln answers with guidance instead, such as {@code owner} or {@code search}. */
    record Unsupported(String endpoint) implements NpmRoute {}

    /** Longer than any package name plus a route; refused before reading. */
    int MAX_LENGTH = NpmPackageName.MAX_LENGTH + 512;

    /** Parses the path below {@code /npm/}, without a leading slash (FMT-NPM-7). */
    static NpmRoute parse(String path) {
        if (path.length() > MAX_LENGTH) {
            throw NpmException.invalid("Invalid path");
        }
        if ("-/ping".equals(path)) {
            return new Ping();
        }
        if ("-/whoami".equals(path)) {
            return new Whoami();
        }
        if (path.startsWith("-/package/")) {
            return NpmRoutePaths.distTags(path.substring("-/package/".length()));
        }
        if (path.startsWith("-/")) {
            return new Unsupported(NpmRoutePaths.endpoint(path));
        }
        return NpmRoutePaths.packageRoute(path);
    }

    /** A dist-tag: npm's own rule is that a tag can't look like a version. */
    static String tag(String value) {
        if (!NpmRoutePaths.TAG.matcher(value).matches() || NpmVersion.isVersion(value)) {
            throw NpmException.invalid("Invalid dist-tag");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
