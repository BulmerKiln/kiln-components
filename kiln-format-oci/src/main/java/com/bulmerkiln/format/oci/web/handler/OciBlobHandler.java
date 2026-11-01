/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.web.handler;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.domain.OciRepositoryName;
import com.bulmerkiln.format.oci.domain.OciRoute;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciUploadResponse;
import com.bulmerkiln.format.oci.service.OciBlobService;
import com.bulmerkiln.format.oci.service.OciUploadService;
import com.bulmerkiln.format.oci.util.OciHeaderUtils;
import com.bulmerkiln.format.oci.util.OciResponseUtils;
import com.bulmerkiln.storage.BlobRead;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Optional;

/** {@code blobs/<digest>} and {@code blobs/uploads/…}: pulls, uploads and mounts. */
public final class OciBlobHandler {

    private final OciBlobService blobs;
    private final OciUploadService uploads;

    public OciBlobHandler(OciBlobService blobs, OciUploadService uploads) {
        this.blobs = blobs;
        this.uploads = uploads;
    }

    public void blob(HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, Digest digest)
            throws IOException {
        boolean head = "HEAD".equals(request.getMethod());
        if (!head && !"GET".equals(request.getMethod())) {
            // Blobs go when GC finds nothing references them, never on request.
            throw OciResponseUtils.unsupported();
        }
        try (BlobRead read = blobs.pull(oci, digest, request.getHeader("Range"))) {
            boolean partial = read.getLength() != read.getBlobSize();
            response.setStatus(partial ? 206 : 200);
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(read.getLength());
            response.setHeader(OciResponseUtils.DIGEST, digest.toString());
            response.setHeader("Accept-Ranges", "bytes");
            if (partial) {
                long last = read.getOffset() + read.getLength() - 1;
                response.setHeader(
                        "Content-Range", "bytes " + read.getOffset() + "-" + last + "/" + read.getBlobSize());
            }
            if (!head) {
                read.transferTo(Channels.newChannel(response.getOutputStream()));
            }
        }
    }

    /** {@code POST blobs/uploads/}: a mount, a single-request upload, or a new session. */
    public void start(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, String organization)
            throws IOException {
        if (!"POST".equals(request.getMethod())) {
            throw OciResponseUtils.unsupported();
        }
        String mount = request.getParameter("mount");
        String from = request.getParameter("from");
        if (mount != null && from != null) {
            Optional<OciUploadResponse> mounted = mountSource(oci, organization, from)
                    .flatMap(source -> uploads.mount(oci, source, OciRoute.digest(mount)));
            if (mounted.isPresent()) {
                OciResponseUtils.stored(response, mounted.get());
                return;
            }
        }
        String digest = request.getParameter("digest");
        if (digest != null) {
            OciResponseUtils.stored(response, uploads.upload(oci, OciRoute.digest(digest), request.getInputStream()));
            return;
        }
        OciResponseUtils.progress(response, uploads.start(oci), 202);
    }

    /** {@code blobs/uploads/<session>}: chunks, status, the final PUT, and cancelling. */
    public void upload(
            HttpServletRequest request, HttpServletResponse response, OciRepositoryRequest oci, String session)
            throws IOException {
        switch (request.getMethod()) {
            case "PATCH" -> {
                Long offset = OciHeaderUtils.contentRangeStart(request.getHeader("Content-Range"));
                OciResponseUtils.progress(response, uploads.patch(oci, session, offset, request.getInputStream()), 202);
            }
            case "PUT" -> {
                String digest = request.getParameter("digest");
                if (digest == null) {
                    throw new OciException(OciErrorCode.DIGEST_INVALID, "The digest parameter is required");
                }
                boolean body = request.getContentLengthLong() != 0;
                OciResponseUtils.stored(
                        response,
                        uploads.finish(oci, session, OciRoute.digest(digest), body ? request.getInputStream() : null));
            }
            case "GET" -> OciResponseUtils.progress(response, uploads.status(oci, session), 204);
            case "DELETE" -> {
                uploads.cancel(oci, session);
                OciResponseUtils.empty(response, 204);
            }
            default -> throw OciResponseUtils.unsupported();
        }
    }

    /** The mount's source within the same organization; any other name is a plain upload. */
    private static Optional<OciRepositoryRequest> mountSource(
            OciRepositoryRequest oci, String organization, String from) {
        if (!from.startsWith(organization)) {
            return Optional.empty();
        }
        try {
            OciRepositoryName name = new OciRepositoryName(from.substring(organization.length()));
            return Optional.of(new OciRepositoryRequest(name, from, oci.getBaseUrl()));
        } catch (OciException e) {
            return Optional.empty();
        }
    }
}
