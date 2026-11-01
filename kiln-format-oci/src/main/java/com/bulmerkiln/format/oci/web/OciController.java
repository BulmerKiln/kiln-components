/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The {@code /v2/} routes, registered only while the OCI format is enabled (FMT-OCI-30). It maps
 * each method the spec uses onto {@link OciRouteDispatcher} and does nothing else. The data plane takes no
 * cookies, so there's no session for a cross-site request to ride on.
 */
@Controller
public final class OciController {

    private static final String ROUTES = "/v2/**";

    private final OciRouteDispatcher routes;

    public OciController(OciRouteDispatcher routes) {
        this.routes = routes;
    }

    @RequestMapping(
            path = {"/v2", "/v2/"},
            method = {RequestMethod.GET, RequestMethod.HEAD})
    void ping(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.ping(request, response);
    }

    @RequestMapping(
            path = ROUTES,
            method = {RequestMethod.GET, RequestMethod.HEAD})
    void read(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    @PutMapping(ROUTES)
    void put(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    @PostMapping(ROUTES)
    void post(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    @PatchMapping(ROUTES)
    void patch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    @DeleteMapping(ROUTES)
    void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }
}
