/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.web;

import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.domain.NpmRoute;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.web.handler.NpmMetaHandler;
import com.bulmerkiln.format.npm.web.handler.NpmPackageHandler;
import com.bulmerkiln.format.npm.web.handler.NpmUnpublishHandler;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Reads the route below {@code /npm/} and hands it on, inside {@link FormatRequests}' scope. */
public final class NpmRouteDispatcher {

    private final FormatRequests requests;
    private final NpmFormatModule format = new NpmFormatModule();
    private final NpmPackageHandler packages;
    private final NpmUnpublishHandler unpublish;

    public NpmRouteDispatcher(FormatRequests requests, NpmPackageHandler packages, NpmUnpublishHandler unpublish) {
        this.requests = requests;
        this.packages = packages;
        this.unpublish = unpublish;
    }

    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rest = request.getRequestURI().substring(request.getContextPath().length() + "/npm/".length());
        requests.scoped(request, response, format, rest, path -> {
            String registry = requests.baseUrl() + "/npm/" + rest.substring(0, rest.length() - path.length());
            switch (NpmRoute.parse(path)) {
                case NpmRoute.Ping _ -> NpmPackageHandler.json(response, 200, "{}");
                case NpmRoute.Whoami _ -> NpmMetaHandler.whoami(response, registry);
                case NpmRoute.Packument p ->
                    packages.packument(request, response, new NpmPackageRequest(p.name(), registry));
                case NpmRoute.Version v ->
                    packages.version(request, response, new NpmPackageRequest(v.name(), registry), v.version());
                case NpmRoute.Tarball t ->
                    packages.tarball(request, response, new NpmPackageRequest(t.name(), registry), t.file());
                case NpmRoute.DistTags t ->
                    packages.distTags(request, response, new NpmPackageRequest(t.name(), registry));
                case NpmRoute.DistTag t ->
                    packages.distTag(request, response, new NpmPackageRequest(t.name(), registry), t.tag());
                case NpmRoute.Revision r ->
                    unpublish.revision(request, response, new NpmPackageRequest(r.name(), registry));
                case NpmRoute.TarballRevision t ->
                    unpublish.tarball(request, response, new NpmPackageRequest(t.name(), registry), t.file());
                case NpmRoute.Unsupported u -> NpmMetaHandler.unsupported(response, u.endpoint());
            }
        });
    }
}
