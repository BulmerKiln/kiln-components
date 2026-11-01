/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.format.oci.domain.OciRepositoryName;
import java.util.Objects;
import lombok.Value;

/**
 * A request for one repository.
 */
@Value
public final class OciRepositoryRequest {

    /** The repository within the organization, as it's stored. */
    private final OciRepositoryName repository;

    /**
     * The name as the client addressed it, which can carry a prefix for the organization; {@code
     * Location} headers and challenges use it.
     */
    private final String addressed;

    /** The configured external URL, such as {@code https://pkg.example.com}, for the token challenge's realm. */
    private final String baseUrl;

    public OciRepositoryRequest(OciRepositoryName repository, String addressed, String baseUrl) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(addressed, "addressed");
        Objects.requireNonNull(baseUrl, "baseUrl");
        this.repository = repository;
        this.addressed = addressed;
        this.baseUrl = baseUrl;
    }

    /**
     * The path of a route below this repository, such as {@code blobs/uploads/<id>}: relative, as the
     * spec allows, so no {@code Location} or {@code Link} carries a host (FMT-OCI-31).
     */
    public String path(String route) {
        return "/v2/" + addressed + "/" + route;
    }
}
