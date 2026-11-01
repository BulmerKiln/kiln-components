/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.domain;

import com.bulmerkiln.format.npm.NpmException;
import java.util.regex.Pattern;

/** The {@code -rev} paths {@code npm unpublish} uses, below a package name (FMT-NPM-17). */
final class NpmRevisionPaths {

    static final String REV = "/-rev/";

    /** A CouchDB-style revision, such as {@code 3-1a2b}; npm echoes what the packument said. */
    private static final Pattern REVISION = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private NpmRevisionPaths() {}

    /** {@code /-rev/<rev>} after the name. */
    static NpmRoute revision(NpmPackageName name, String rest) {
        requireRevision(rest.substring(REV.length()));
        return new NpmRoute.Revision(name);
    }

    /** {@code <file>/-rev/<rev>} after {@code <name>/-/}, where {@code at} is where {@code /-rev/} starts. */
    static NpmRoute tarball(NpmPackageName name, String file, int at) {
        String tarball = file.substring(0, at);
        if (!NpmRoutePaths.TARBALL.matcher(tarball).matches()) {
            throw NpmException.notFound();
        }
        requireRevision(file.substring(at + REV.length()));
        return new NpmRoute.TarballRevision(name, tarball);
    }

    private static void requireRevision(String revision) {
        if (!REVISION.matcher(revision).matches()) {
            throw NpmException.notFound();
        }
    }
}
