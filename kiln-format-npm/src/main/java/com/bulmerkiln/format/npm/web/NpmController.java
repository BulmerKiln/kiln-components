/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The {@code /npm/} routes, registered only while the npm format is enabled (FMT-NPM-15). It maps
 * each method npm uses onto {@link NpmRouteDispatcher} and does nothing else. The data plane takes no cookies,
 * so there's no session for a cross-site request to ride on.
 */
@Controller
public final class NpmController {

    private static final String ROUTES = "/npm/**";

    private final NpmRouteDispatcher routes;

    public NpmController(NpmRouteDispatcher routes) {
        this.routes = routes;
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

    /** npm only posts to endpoints Kiln doesn't support yet, such as login and audit. */
    @PostMapping(ROUTES)
    void post(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    @DeleteMapping(ROUTES)
    void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }
}
