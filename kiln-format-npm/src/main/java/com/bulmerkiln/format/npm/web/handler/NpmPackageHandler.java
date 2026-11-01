/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.web.handler;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.dto.FormatResponse;
import com.bulmerkiln.format.npm.domain.NpmPackumentBuilder;
import com.bulmerkiln.format.npm.domain.NpmVersion;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.service.NpmPublishService;
import com.bulmerkiln.format.npm.service.NpmReadService;
import com.bulmerkiln.format.npm.service.NpmTagService;
import com.bulmerkiln.storage.BlobRead;
import com.bulmerkiln.web.FormatRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Map;

/** A package's routes: its packument, versions, tarballs and dist-tags, and publishing to it. */
public final class NpmPackageHandler {

    static final String JSON = "application/json";
    private static final String ABBREVIATED = "application/vnd.npm.install-v1+json";

    private final NpmReadService reads;
    private final NpmPublishService publisher;
    private final NpmTagService tags;

    public NpmPackageHandler(NpmReadService reads, NpmPublishService publisher, NpmTagService tags) {
        this.reads = reads;
        this.publisher = publisher;
        this.tags = tags;
    }

    public void version(
            HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm, NpmVersion version)
            throws IOException {
        read(request, response, () -> reads.version(npm, version));
    }

    public void distTags(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm)
            throws IOException {
        read(request, response, () -> NpmPackumentBuilder.JSON.writeValueAsString(reads.distTags(npm)));
    }

    public void packument(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm)
            throws IOException {
        switch (request.getMethod()) {
            case "GET", "HEAD" -> {
                String accept = request.getHeader("Accept");
                boolean abbreviated = accept != null && accept.contains(ABBREVIATED);
                write(response, 200, abbreviated ? ABBREVIATED : JSON, reads.packument(npm, abbreviated));
            }
            case "PUT" -> {
                publisher.put(npm, request.getInputStream());
                json(response, 201, "{\"ok\":true}");
            }
            default -> throw unsupportedMethod();
        }
    }

    public void tarball(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm, String file)
            throws IOException {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            throw unsupportedMethod();
        }
        try (BlobRead read = reads.tarball(npm, file)) {
            response.setStatus(200);
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(read.getLength());
            if ("GET".equals(request.getMethod())) {
                read.transferTo(Channels.newChannel(response.getOutputStream()));
            }
        }
    }

    public void distTag(HttpServletRequest request, HttpServletResponse response, NpmPackageRequest npm, String tag)
            throws IOException {
        switch (request.getMethod()) {
            case "PUT" -> {
                String version = NpmPackumentBuilder.JSON.readValue(request.getInputStream(), String.class);
                tags.set(npm, tag, new NpmVersion(version));
                json(response, 201, "{\"ok\":true}");
            }
            case "DELETE" -> {
                tags.remove(npm, tag);
                json(response, 200, "{\"ok\":true}");
            }
            default -> throw unsupportedMethod();
        }
    }

    private void read(HttpServletRequest request, HttpServletResponse response, ReadBody body) throws IOException {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            throw unsupportedMethod();
        }
        json(response, 200, body.read());
    }

    @FunctionalInterface
    private interface ReadBody {
        String read() throws IOException;
    }

    public static void json(HttpServletResponse response, int status, String body) throws IOException {
        write(response, status, JSON, body);
    }

    static void write(HttpServletResponse response, int status, String contentType, String body) throws IOException {
        FormatRequests.write(response, new FormatResponse(status, contentType, body, Map.of()));
    }

    static RefusedException unsupportedMethod() {
        return new RefusedException(Refusal.of(Reason.UNSUPPORTED, "Method not allowed"));
    }
}
