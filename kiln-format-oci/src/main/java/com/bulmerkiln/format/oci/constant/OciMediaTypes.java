/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.constant;

import java.util.Set;

/** OciManifest media types the registry accepts, and the Docker schema1 ones it refuses. */
public final class OciMediaTypes {

    public static final String OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    public static final String OCI_INDEX = "application/vnd.oci.image.index.v1+json";
    public static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    public static final String DOCKER_MANIFEST_LIST = "application/vnd.docker.distribution.manifest.list.v2+json";

    public static final Set<String> IMAGE_MANIFESTS = Set.of(OCI_MANIFEST, DOCKER_MANIFEST);
    public static final Set<String> INDEXES = Set.of(OCI_INDEX, DOCKER_MANIFEST_LIST);
    public static final Set<String> DOCKER_SCHEMA1 = Set.of(
            "application/vnd.docker.distribution.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v1+prettyjws");

    private OciMediaTypes() {}
}
