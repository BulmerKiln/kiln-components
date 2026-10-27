/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.NpmException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Splits a path into its package name and what follows, for {@link NpmRoute#parse}. */
final class NpmRoutePaths {

    static final Pattern TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Pattern TARBALL = Pattern.compile("[A-Za-z0-9._-]{1,250}\\.tgz");

    private NpmRoutePaths() {}

    /** {@code <name>}, {@code <name>/<version>} or {@code <name>/-/<file>.tgz}. */
    static NpmRoute packageRoute(String path) {
        int end = nameEnd(path);
        NpmPackageName name = NpmPackageName.fromPath(path.substring(0, end));
        String rest = path.substring(end);
        if (rest.isEmpty()) {
            return new NpmRoute.Packument(name);
        }
        if (rest.startsWith("/-/")) {
            String file = rest.substring(3);
            if (!TARBALL.matcher(file).matches()) {
                throw notFound();
            }
            return new NpmRoute.Tarball(name, file);
        }
        if (rest.startsWith("/-rev/")) {
            return new NpmRoute.Unsupported("unpublish");
        }
        String version = rest.substring(1);
        if (version.indexOf('/') >= 0) {
            throw notFound();
        }
        return new NpmRoute.Version(name, new NpmVersion(version));
    }

    /** {@code <name>/dist-tags} and {@code <name>/dist-tags/<tag>}, below {@code -/package/}. */
    static NpmRoute distTags(String path) {
        int end = nameEnd(path);
        NpmPackageName name = NpmPackageName.fromPath(path.substring(0, end));
        String rest = path.substring(end);
        if ("/dist-tags".equals(rest)) {
            return new NpmRoute.DistTags(name);
        }
        if (rest.startsWith("/dist-tags/")) {
            return new NpmRoute.DistTag(name, NpmRoute.tag(rest.substring("/dist-tags/".length())));
        }
        return new NpmRoute.Unsupported("access");
    }

    /** The npm command an unsupported {@code -/} path belongs to, for the guidance message. */
    static String endpoint(String path) {
        String[] parts = path.split("/", 4);
        String endpoint = parts.length > 2 && "v1".equals(parts[1]) ? parts[2] : parts.length > 1 ? parts[1] : "";
        return TAG.matcher(endpoint).matches() ? endpoint.toLowerCase(Locale.ROOT) : "this";
    }

    /**
     * Where the package name ends: after {@code @scope%2fname} or {@code @scope/name} for a scoped
     * name, after the first segment otherwise.
     */
    private static int nameEnd(String path) {
        int first = path.indexOf('/');
        int firstEnd = first < 0 ? path.length() : first;
        if (!path.startsWith("@")
                || path.substring(0, firstEnd).toLowerCase(Locale.ROOT).contains("%2f")) {
            return firstEnd;
        }
        if (first < 0) {
            throw NpmException.invalid("Invalid package name");
        }
        int second = path.indexOf('/', first + 1);
        return second < 0 ? path.length() : second;
    }

    private static NpmException notFound() {
        return NpmException.notFound();
    }
}
