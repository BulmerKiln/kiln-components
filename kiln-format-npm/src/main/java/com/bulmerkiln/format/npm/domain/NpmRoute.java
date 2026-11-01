/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.NpmException;
import java.util.Locale;
import lombok.Value;

/**
 * A registry route below {@code /npm/}. Scoped names arrive as {@code @scope/name} or {@code
 * @scope%2fname}; {@link NpmPackageName#fromPath} decides which forms are accepted (FMT-NPM-7).
 */
public sealed interface NpmRoute {

    @Value
    static final class Ping implements NpmRoute {}

    @Value
    static final class Whoami implements NpmRoute {}

    @Value
    static final class Packument implements NpmRoute {

        private final NpmPackageName name;
    }

    @Value
    static final class Version implements NpmRoute {

        private final NpmPackageName name;

        private final NpmVersion version;
    }

    @Value
    static final class Tarball implements NpmRoute {

        private final NpmPackageName name;

        private final String file;
    }

    @Value
    static final class DistTags implements NpmRoute {

        private final NpmPackageName name;
    }

    @Value
    static final class DistTag implements NpmRoute {

        private final NpmPackageName name;

        private final String tag;
    }

    /** {@code <name>/-rev/<rev>}, where {@code npm unpublish} rewrites or deletes a package (FMT-NPM-17). */
    @Value
    static final class Revision implements NpmRoute {

        private final NpmPackageName name;
    }

    /** {@code <name>/-/<file>/-rev/<rev>}, the tarball {@code npm unpublish} deletes after its version. */
    @Value
    static final class TarballRevision implements NpmRoute {

        private final NpmPackageName name;

        private final String file;
    }

    /** An npm endpoint Kiln answers with guidance instead, such as {@code owner} or {@code search}. */
    @Value
    static final class Unsupported implements NpmRoute {

        private final String endpoint;
    }

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
