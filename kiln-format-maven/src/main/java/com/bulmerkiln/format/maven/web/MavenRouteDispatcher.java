/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.web;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.constant.MavenChecksumAlgorithm;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileService;
import com.bulmerkiln.format.maven.service.MavenMetadataService;
import com.bulmerkiln.format.maven.store.MavenFileStore;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Reads the path below {@code /maven/} and answers it, inside {@link FormatRequests}' scope. Every
 * path takes {@code GET}, {@code HEAD} and {@code PUT}; deletes go through Kiln, not Maven.
 */
public final class MavenRouteDispatcher {

    private static final String TEXT = "text/plain; charset=utf-8";
    private static final int MAX_METADATA = 1024 * 1024;

    private final FormatRequests requests;
    private final MavenFormatModule format = new MavenFormatModule();
    private final MavenFileService files;
    private final MavenMetadataService metadata;
    private final MavenAccessService access;

    public MavenRouteDispatcher(
            FormatRequests requests, MavenFileService files, MavenMetadataService metadata, MavenAccessService access) {
        this.requests = requests;
        this.files = files;
        this.metadata = metadata;
        this.access = access;
    }

    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rest = request.getRequestURI().substring(request.getContextPath().length() + "/maven/".length());
        requests.scoped(request, response, format, rest, path -> {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            switch (MavenPath.parse(path)) {
                case MavenPath.ArtifactFile file -> file(request, response, file, fileName);
                case MavenPath.Metadata m -> metadata(request, response, m);
            }
        });
    }

    private void file(
            HttpServletRequest request, HttpServletResponse response, MavenPath.ArtifactFile file, String fileName)
            throws IOException {
        String method = request.getMethod();
        if ("PUT".equals(method)) {
            files.deploy(file, fileName, request.getInputStream());
            empty(response, 201);
            return;
        }
        requireRead(method);
        MavenChecksumAlgorithm checksum = file.checksum();
        if (checksum != null) {
            text(response, files.checksum(file, fileName, checksum));
            return;
        }
        try (BlobRead read = files.download(file, fileName)) {
            response.setStatus(200);
            response.setContentType(MavenFileService.isPom(file) ? "text/xml" : "application/octet-stream");
            response.setContentLengthLong(read.length());
            if ("GET".equals(method)) {
                read.transferTo(Channels.newChannel(response.getOutputStream()));
            }
        }
    }

    /** Generated metadata and its checksums; uploaded metadata is accepted and then ignored. */
    private void metadata(HttpServletRequest request, HttpServletResponse response, MavenPath.Metadata m)
            throws IOException {
        if ("PUT".equals(request.getMethod())) {
            access.requireToWrite(m);
            discard(request);
            empty(response, 201);
            return;
        }
        requireRead(request.getMethod());
        byte[] xml = metadata.read(m);
        MavenChecksumAlgorithm checksum = m.checksum();
        if (checksum != null) {
            text(response, MavenFileStore.checksum(xml, checksum));
            return;
        }
        FormatRequests.write(
                response, new FormatResponse(200, "text/xml", new String(xml, StandardCharsets.UTF_8), Map.of()));
    }

    /** Reads an upload Kiln ignores, refusing one too large to be metadata or a checksum. */
    private static void discard(HttpServletRequest request) throws IOException {
        InputStream body = request.getInputStream();
        body.readNBytes(MAX_METADATA);
        if (body.read() != -1) {
            throw new RefusedException(
                    Refusal.of(Reason.TOO_LARGE, "Metadata is limited to " + MAX_METADATA + " bytes"));
        }
    }

    private static void requireRead(String method) {
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            throw new RefusedException(Refusal.of(Reason.UNSUPPORTED, "Method not allowed"));
        }
    }

    private static void text(HttpServletResponse response, String body) throws IOException {
        FormatRequests.write(response, new FormatResponse(200, TEXT, body, Map.of()));
    }

    private static void empty(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setContentLength(0);
    }
}
