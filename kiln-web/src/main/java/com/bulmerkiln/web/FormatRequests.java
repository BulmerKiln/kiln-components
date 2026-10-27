/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.format.FormatModule;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Principal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The scope every format request runs in. It resolves the organization from the artifact path and
 * the caller from the credentials, binds both for exactly the handler's duration (WEB-1), and
 * answers any refusal in the format's own error shape (WEB-2). Controllers only translate HTTP.
 */
public final class FormatRequests {

    private final OrganizationResolver organizations;
    private final PrincipalResolver principals;
    private final String baseUrl;

    public FormatRequests(OrganizationResolver organizations, PrincipalResolver principals, String baseUrl) {
        this.organizations = organizations;
        this.principals = principals;
        this.baseUrl = baseUrl;
    }

    /** A handler for a route that names an artifact; it gets the path within the organization. */
    @FunctionalInterface
    public interface ScopedHandler {
        void handle(String path) throws IOException;
    }

    /** A handler for a route that names no artifact, such as {@code GET /v2/}. */
    @FunctionalInterface
    public interface Handler {
        void handle() throws IOException;
    }

    /**
     * Runs {@code handler} for the organization {@code path} names. An unknown organization is
     * answered as not found, the same as an artifact that doesn't exist, so organizations can't be
     * discovered by probing (WEB-1).
     */
    public void scoped(
            HttpServletRequest request,
            HttpServletResponse response,
            FormatModule format,
            String path,
            ScopedHandler handler)
            throws IOException {
        refusingIn(format, response, () -> {
            OrganizationResolver.Resolution resolution = organizations
                    .resolve(path)
                    .orElseThrow(() -> new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found")));
            Principal principal = principals.resolve(request);
            OrganizationContext.call(
                    resolution.organization(),
                    () -> CurrentPrincipal.call(principal, () -> {
                        handler.handle(resolution.path());
                        return null;
                    }));
        });
    }

    /** Runs {@code handler} for the caller, outside any organization. */
    public void unscoped(HttpServletRequest request, HttpServletResponse response, FormatModule format, Handler handler)
            throws IOException {
        refusingIn(format, response, () -> {
            Principal principal = principals.resolve(request);
            CurrentPrincipal.call(principal, () -> {
                handler.handle();
                return null;
            });
        });
    }

    /**
     * The URL clients reach Kiln at, such as {@code https://pkg.example.com}, for absolute {@code
     * Location} headers, token realms and links. It's configured, never taken from the request
     * (WEB-5).
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Writes a format response: status, content type, headers and a UTF-8 body. Formats use it for
     * their small JSON and text answers as well as refusals.
     */
    public static void write(HttpServletResponse response, FormatResponse answer) throws IOException {
        response.setStatus(answer.status());
        response.setContentType(answer.contentType());
        for (Map.Entry<String, String> header : answer.headers().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static void refusingIn(FormatModule format, HttpServletResponse response, Handler work) throws IOException {
        try {
            work.handle();
        } catch (RefusedException e) {
            if (response.isCommitted()) {
                // Part of an answer is already sent; a refusal can't follow it, so the stream is cut.
                throw e;
            }
            response.reset();
            write(response, FormatResponse.refusal(format, e.refusal()));
        }
    }
}
