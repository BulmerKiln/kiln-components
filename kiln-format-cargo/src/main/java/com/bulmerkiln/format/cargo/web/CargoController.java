/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.web;

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
 * The {@code /cargo/} routes, registered only while the Cargo format is enabled (FMT-CARGO-13). It
 * maps each method onto {@link CargoRouteDispatcher} and does nothing else. The data plane takes no cookies,
 * so there's no session for a cross-site request to ride on.
 */
@Controller
public final class CargoController {

    private static final String ROUTES = "/cargo/**";

    private final CargoRouteDispatcher routes;

    public CargoController(CargoRouteDispatcher routes) {
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

    /** Answered in Cargo's terms rather than Spring's. */
    @PostMapping(ROUTES)
    void post(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }

    /** Answered in Cargo's terms rather than Spring's. */
    @DeleteMapping(ROUTES)
    void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        routes.handle(request, response);
    }
}
