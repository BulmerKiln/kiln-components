/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.dto;

import com.bulmerkiln.format.npm.domain.NpmPackageName;
import java.util.Objects;

/**
 * A request about one package.
 *
 * @param name the package
 * @param registryUrl the registry's URL as the client uses it, such as {@code
 *     https://pkg.example.com/npm/}, ending in a slash; tarball links are built from it
 */
public record NpmPackageRequest(NpmPackageName name, String registryUrl) {

    public NpmPackageRequest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(registryUrl, "registryUrl");
    }

    /** The tarball file for a version: the name without its scope, then the version. */
    public String tarballFile(String version) {
        return name.name() + "-" + version + ".tgz";
    }

    public String tarballUrl(String version) {
        return registryUrl + name.urlEncoded() + "/-/" + tarballFile(version);
    }
}
