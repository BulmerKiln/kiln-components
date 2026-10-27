/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.web.handler;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** The registry's own routes: {@code ping}, {@code whoami}, and guidance for what Kiln doesn't serve. */
public final class NpmMetaHandler {

    private NpmMetaHandler() {}

    /** {@code npm whoami}: who the credentials belong to; anyone without them is asked for some. */
    public static void whoami(HttpServletResponse response, String registry) throws IOException {
        String username = switch (CurrentPrincipal.get()) {
            case Principal.Member member -> member.id().value().toString();
            case Principal.ServiceAccount account -> account.id().value().toString();
            case Principal.Workload workload -> workload.publisher();
            case Principal.Anonymous _ ->
                throw new RefusedException(Refusal.unauthorized("Sign in first", "Bearer realm=\"" + registry + "\""));
        };
        NpmPackageHandler.json(
                response, 200, NpmPackumentBuilder.JSON.writeValueAsString(Map.of("username", username)));
    }

    /** npm commands Kiln answers elsewhere: a {@code 404}, never a {@code 5xx}, with guidance. */
    public static void unsupported(HttpServletResponse response, String endpoint) throws IOException {
        String message = "npm " + endpoint + " isn't supported by Kiln; use the Kiln web app or the kiln CLI";
        FormatRequests.write(
                response,
                new FormatResponse(
                        404,
                        NpmPackageHandler.JSON,
                        NpmPackumentBuilder.JSON.writeValueAsString(Map.of("error", message)),
                        Map.of("npm-notice", message, "X-Content-Type-Options", "nosniff")));
    }
}
