/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web.handler;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.format.oci.dto.OciReferrersResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciTagPageResponse;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.service.OciTagService;
import com.bulmerkiln.format.oci.util.OciHeaderUtils;
import com.bulmerkiln.format.oci.util.OciResponseUtils;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** {@code tags/list} and {@code referrers/<digest>}. */
public final class OciTagHandler {

    private final OciTagService tags;

    OciTagHandler(OciTagService tags) {
        this.tags = tags;
    }

    public static OciTagHandler of(Database database, OciAccessService access, int maxPageSize) {
        return new OciTagHandler(new OciTagService(database, access, maxPageSize));
    }

    public void tags(HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci)
            throws IOException {
        if (!"GET".equals(request.getMethod())) {
            throw OciResponseUtils.unsupported();
        }
        OciTagPageResponse page =
                tags.tags(oci, OciHeaderUtils.pageSize(request.getParameter("n")), request.getParameter("last"));
        String next = page.next();
        Map<String, String> headers = next == null ? Map.of() : Map.of("Link", "<" + next + ">; rel=\"next\"");
        FormatRequests.write(response, new FormatResponse(200, "application/json", page.body(), headers));
    }

    public void referrers(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, Digest digest)
            throws IOException {
        if (!"GET".equals(request.getMethod())) {
            throw OciResponseUtils.unsupported();
        }
        OciReferrersResponse referrers = tags.referrers(oci, digest, request.getParameter("artifactType"));
        Map<String, String> headers = referrers.filtered() ? Map.of("OCI-Filters-Applied", "artifactType") : Map.of();
        FormatRequests.write(response, new FormatResponse(200, OciMediaTypes.OCI_INDEX, referrers.body(), headers));
    }
}
