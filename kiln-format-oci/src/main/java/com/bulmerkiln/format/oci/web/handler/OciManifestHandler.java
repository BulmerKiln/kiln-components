/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web.handler;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.domain.OciReference;
import com.bulmerkiln.format.oci.domain.OciStoredManifest;
import com.bulmerkiln.format.oci.dto.OciManifestPushResponse;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.service.OciDeleteService;
import com.bulmerkiln.format.oci.service.OciManifestService;
import com.bulmerkiln.format.oci.util.OciResponseUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** {@code manifests/<reference>}: pull, push and delete. */
public final class OciManifestHandler {

    private final OciManifestService manifests;
    private final OciDeleteService deletes;
    private final int maxManifestSize;

    public OciManifestHandler(OciManifestService manifests, OciDeleteService deletes, int maxManifestSize) {
        this.manifests = manifests;
        this.deletes = deletes;
        this.maxManifestSize = maxManifestSize;
    }

    public void handle(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, OciReference reference)
            throws IOException {
        switch (request.getMethod()) {
            case "GET", "HEAD" -> pull(response, oci, reference, "HEAD".equals(request.getMethod()));
            case "PUT" -> push(request, response, oci, reference);
            case "DELETE" -> {
                deletes.delete(oci, reference);
                OciResponseUtils.empty(response, 202);
            }
            default -> throw OciResponseUtils.unsupported();
        }
    }

    private void pull(HttpServletResponse response, OciRepositoryRequest oci, OciReference reference, boolean head)
            throws IOException {
        OciStoredManifest manifest = manifests.pull(oci, reference, head);
        response.setStatus(200);
        response.setContentType(manifest.getRecord().getMediaType());
        response.setHeader(
                OciResponseUtils.DIGEST, manifest.getRecord().getDigest().toString());
        response.setContentLength(manifest.getContent().length);
        if (!head) {
            response.getOutputStream().write(manifest.getContent());
        }
    }

    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = OciResponseUtils.LOCATION)
    private void push(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, OciReference reference)
            throws IOException {
        // One byte past the limit, so the parser can tell an oversized manifest from one at the limit.
        byte[] content = request.getInputStream().readNBytes(maxManifestSize + 1);
        OciManifestPushResponse pushed = manifests.push(oci, reference, request.getContentType(), content);
        response.setHeader("Location", oci.path("manifests/" + pushed.getDigest()));
        response.setHeader(OciResponseUtils.DIGEST, pushed.getDigest().toString());
        Digest subject = pushed.getSubject();
        if (subject != null) {
            response.setHeader("OCI-Subject", subject.toString());
        }
        OciResponseUtils.empty(response, 201);
    }
}
