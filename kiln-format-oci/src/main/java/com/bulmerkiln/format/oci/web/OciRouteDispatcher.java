/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web;

import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.oci.OciFormatModule;
import com.bulmerkiln.format.oci.domain.OciRoute;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.web.handler.OciBlobHandler;
import com.bulmerkiln.format.oci.web.handler.OciManifestHandler;
import com.bulmerkiln.format.oci.web.handler.OciTagHandler;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Reads the route below {@code /v2/} and hands it to the routes for manifests, blobs or tags, inside
 * {@link FormatRequests}' scope.
 */
public final class OciRouteDispatcher {

    private static final String API_VERSION = "Docker-Distribution-API-Version";

    private final FormatRequests requests;
    private final OciFormatModule format = new OciFormatModule();
    private final OciManifestHandler manifests;
    private final OciBlobHandler blobs;
    private final OciTagHandler tags;

    public OciRouteDispatcher(
            FormatRequests requests, OciManifestHandler manifests, OciBlobHandler blobs, OciTagHandler tags) {
        this.requests = requests;
        this.manifests = manifests;
        this.blobs = blobs;
        this.tags = tags;
    }

    /** {@code GET /v2/}: docker's first request, which decides its auth flow (FMT-OCI-28). */
    void ping(HttpServletRequest request, HttpServletResponse response) throws IOException {
        requests.unscoped(
                request,
                response,
                format,
                () -> FormatRequests.write(
                        response,
                        new FormatResponse(200, "application/json", "{}", Map.of(API_VERSION, "registry/2.0"))));
    }

    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rest = request.getRequestURI().substring(request.getContextPath().length() + "/v2/".length());
        requests.scoped(request, response, format, rest, path -> {
            response.setHeader(API_VERSION, "registry/2.0");
            OciRoute route = OciRoute.parse(path);
            String organization = rest.substring(0, rest.length() - path.length());
            OciRepositoryRequest oci = new OciRepositoryRequest(
                    route.getRepository(), organization + route.getRepository().getValue(), requests.baseUrl());
            switch (route) {
                case OciRoute.Manifest m -> manifests.handle(request, response, oci, m.getReference());
                case OciRoute.Blob b -> blobs.blob(request, response, oci, b.getDigest());
                case OciRoute.Uploads _ -> blobs.start(request, response, oci, organization);
                case OciRoute.Upload u -> blobs.upload(request, response, oci, u.getSession());
                case OciRoute.Tags _ -> tags.tags(request, response, oci);
                case OciRoute.Referrers r -> tags.referrers(request, response, oci, r.getDigest());
            }
        });
    }
}
