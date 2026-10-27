/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.web;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.CargoFormatModule;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.cargo.domain.CargoIndexEntry;
import com.bulmerkiln.format.cargo.domain.CargoRoute;
import com.bulmerkiln.format.cargo.dto.CargoIndexFileResponse;
import com.bulmerkiln.format.cargo.service.CargoPublishService;
import com.bulmerkiln.format.cargo.service.CargoReadService;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Reads the route below {@code /cargo/} and answers it, inside {@link FormatRequests}' scope. */
public final class CargoRouteDispatcher {

    private static final String JSON = "application/json";
    private static final String OK = "{\"ok\":true}";

    private final FormatRequests requests;
    private final CargoFormatModule format = new CargoFormatModule();
    private final CargoReadService reads;
    private final CargoPublishService publisher;

    public CargoRouteDispatcher(FormatRequests requests, CargoReadService reads, CargoPublishService publisher) {
        this.requests = requests;
        this.reads = reads;
        this.publisher = publisher;
    }

    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rest = request.getRequestURI().substring(request.getContextPath().length() + "/cargo/".length());
        requests.scoped(request, response, format, rest, path -> {
            String registry = requests.baseUrl() + "/cargo/" + rest.substring(0, rest.length() - path.length());
            String method = request.getMethod();
            switch (CargoRoute.parse(path)) {
                case CargoRoute.Config _ -> config(method, response, registry);
                case CargoRoute.Index i -> index(request, response, i.name());
                case CargoRoute.Publish _ -> {
                    requireMethod(method, "PUT");
                    publisher.publish(request.getInputStream());
                    json(response, "{\"warnings\":{\"invalid_categories\":[],\"invalid_badges\":[],\"other\":[]}}");
                }
                case CargoRoute.Download d -> download(method, response, d);
                case CargoRoute.Yank y -> {
                    requireMethod(method, y.yanked() ? "DELETE" : "PUT");
                    reads.yank(y.name(), y.version(), y.yanked());
                    json(response, OK);
                }
                case CargoRoute.Unsupported u ->
                    throw new CargoException(
                            Reason.NOT_FOUND,
                            "cargo " + u.command() + " isn't supported by Kiln; use the Kiln web app or the kiln CLI");
            }
        });
    }

    /**
     * Where downloads and the API are, and that every request needs credentials. Readable by anyone,
     * since Cargo asks for it before it sends any.
     */
    private static void config(String method, HttpServletResponse response, String registry) throws IOException {
        requireRead(method);
        String base = registry.substring(0, registry.length() - 1);
        json(
                response,
                CargoIndexEntry.JSON.writeValueAsString(
                        Map.of("dl", base + "/api/v1/crates", "api", base, "auth-required", true)));
    }

    /** An index file, with an {@code ETag} and {@code Last-Modified} so Cargo's cache works. */
    private void index(HttpServletRequest request, HttpServletResponse response, CargoCrateName name)
            throws IOException {
        requireRead(request.getMethod());
        CargoIndexFileResponse file = reads.index(name);
        String etag = "\""
                + Digest.sha256(file.body().getBytes(StandardCharsets.UTF_8)).encoded() + "\"";
        String modified =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(file.modified().atOffset(ZoneOffset.UTC));
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(304);
            response.setHeader("ETag", etag);
            return;
        }
        FormatRequests.write(
                response,
                new FormatResponse(
                        200,
                        "text/plain; charset=utf-8",
                        file.body(),
                        Map.of("ETag", etag, "Last-Modified", modified)));
    }

    private void download(String method, HttpServletResponse response, CargoRoute.Download route) throws IOException {
        requireRead(method);
        try (BlobRead read = reads.download(route.name(), route.version())) {
            response.setStatus(200);
            response.setContentType("application/gzip");
            response.setContentLengthLong(read.length());
            if ("GET".equals(method)) {
                read.transferTo(Channels.newChannel(response.getOutputStream()));
            }
        }
    }

    private static void json(HttpServletResponse response, String body) throws IOException {
        FormatRequests.write(response, new FormatResponse(200, JSON, body, Map.of()));
    }

    private static void requireRead(String method) {
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            throw unsupportedMethod();
        }
    }

    private static void requireMethod(String method, String expected) {
        if (!expected.equals(method)) {
            throw unsupportedMethod();
        }
    }

    private static RefusedException unsupportedMethod() {
        return new RefusedException(Refusal.of(Reason.UNSUPPORTED, "Method not allowed"));
    }
}
