/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.dto;

import com.bulmerkiln.format.npm.domain.NpmPackageName;
import java.util.Objects;
import lombok.Value;

/**
 * A request about one package.
 */
@Value
public final class NpmPackageRequest {

    /** The package. */
    private final NpmPackageName name;

    /**
     * The registry's URL as the client uses it, such as {@code https://pkg.example.com/npm/}, ending
     * in a slash; tarball links are built from it.
     */
    private final String registryUrl;

    public NpmPackageRequest(NpmPackageName name, String registryUrl) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(registryUrl, "registryUrl");
        this.name = name;
        this.registryUrl = registryUrl;
    }

    /** The tarball file for a version: the name without its scope, then the version. */
    public String tarballFile(String version) {
        return name.getName() + "-" + version + ".tgz";
    }

    public String tarballUrl(String version) {
        return registryUrl + name.urlEncoded() + "/-/" + tarballFile(version);
    }
}
