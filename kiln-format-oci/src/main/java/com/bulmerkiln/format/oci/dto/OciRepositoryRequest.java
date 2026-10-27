/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.format.oci.domain.OciRepositoryName;
import java.util.Objects;

/**
 * A request for one repository.
 *
 * @param repository the repository within the organization, as it's stored
 * @param addressed the name as the client addressed it, which can carry a prefix for the
 *     organization; {@code Location} headers and challenges use it
 * @param baseUrl where the client reached Kiln, such as {@code https://pkg.example.com}
 */
public record OciRepositoryRequest(OciRepositoryName repository, String addressed, String baseUrl) {

    public OciRepositoryRequest {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(addressed, "addressed");
        Objects.requireNonNull(baseUrl, "baseUrl");
    }

    /** The absolute URL of a route below this repository, such as {@code blobs/uploads/<id>}. */
    public String url(String route) {
        return baseUrl + "/v2/" + addressed + "/" + route;
    }
}
