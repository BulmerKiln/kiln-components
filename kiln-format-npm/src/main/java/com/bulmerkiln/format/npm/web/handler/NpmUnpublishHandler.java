/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.web.handler;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.service.NpmAccessService;
import com.bulmerkiln.format.npm.service.NpmUnpublishService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** The {@code -rev} routes {@code npm unpublish} uses (FMT-NPM-17). */
public final class NpmUnpublishHandler {

    private static final String OK = "{\"ok\":true}";

    private final NpmUnpublishService unpublish;

    public NpmUnpublishHandler(NpmUnpublishService unpublish) {
        this.unpublish = unpublish;
    }

    public static NpmUnpublishHandler of(Database database, NpmAccessService access) {
        return new NpmUnpublishHandler(new NpmUnpublishService(database, access));
    }

    /** {@code PUT} drops the versions the packument leaves out; {@code DELETE} drops the package. */
    public void revision(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm)
            throws IOException {
        switch (request.getMethod()) {
            case "PUT" -> unpublish.removeVersions(npm, request.getInputStream());
            case "DELETE" -> unpublish.removePackage(npm);
            default -> throw NpmPackageHandler.unsupportedMethod();
        }
        NpmPackageHandler.json(response, 200, OK);
    }

    public void tarball(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm, String file)
            throws IOException {
        if (!"DELETE".equals(request.getMethod())) {
            throw NpmPackageHandler.unsupportedMethod();
        }
        unpublish.removeTarball(npm, file);
        NpmPackageHandler.json(response, 200, OK);
    }
}
