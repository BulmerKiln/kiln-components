/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web.handler;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.dto.OciManifestPushResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.service.OciManifestService;
import com.bulmerkiln.format.oci.util.OciResponseUtils;
import com.bulmerkiln.format.service.ArtifactEventService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** {@code manifests/<reference>}: pull, push and delete. */
public final class OciManifestHandler {

    private final OciManifestService manifests;
    private final int maxManifestSize;

    OciManifestHandler(OciManifestService manifests, int maxManifestSize) {
        this.manifests = manifests;
        this.maxManifestSize = maxManifestSize;
    }

    public static OciManifestHandler of(
            Database database, OciAccessService access, ArtifactEventService events, int maxManifestSize) {
        return new OciManifestHandler(
                new OciManifestService(database, access, events, maxManifestSize), maxManifestSize);
    }

    public void handle(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, OciReference reference)
            throws IOException {
        switch (request.getMethod()) {
            case "GET", "HEAD" -> pull(response, oci, reference, "HEAD".equals(request.getMethod()));
            case "PUT" -> push(request, response, oci, reference);
            case "DELETE" -> {
                manifests.delete(oci, reference);
                OciResponseUtils.empty(response, 202);
            }
            default -> throw OciResponseUtils.unsupported();
        }
    }

    private void pull(HttpServletResponse response, OciRepositoryRequest oci, OciReference reference, boolean head)
            throws IOException {
        OciManifestEntity manifest = manifests.pull(oci, reference, head);
        response.setStatus(200);
        response.setContentType(manifest.mediaType());
        response.setHeader(OciResponseUtils.DIGEST, manifest.digest().toString());
        response.setContentLength(manifest.content().length);
        if (!head) {
            response.getOutputStream().write(manifest.content());
        }
    }

    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = OciResponseUtils.LOCATION)
    private void push(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, OciReference reference)
            throws IOException {
        // One byte past the limit, so the parser can tell an oversized manifest from one at the limit.
        byte[] content = request.getInputStream().readNBytes(maxManifestSize + 1);
        OciManifestPushResponse pushed = manifests.push(oci, reference, request.getContentType(), content);
        response.setHeader("Location", oci.path("manifests/" + pushed.digest()));
        response.setHeader(OciResponseUtils.DIGEST, pushed.digest().toString());
        Digest subject = pushed.subject();
        if (subject != null) {
            response.setHeader("OCI-Subject", subject.toString());
        }
        OciResponseUtils.empty(response, 201);
    }
}
