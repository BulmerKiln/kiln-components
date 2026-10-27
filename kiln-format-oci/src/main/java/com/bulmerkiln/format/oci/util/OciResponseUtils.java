/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.util;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.format.oci.dto.OciUploadProgressResponse;
import com.bulmerkiln.format.oci.dto.OciUploadResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;

/** The spec's bodiless answers, and its headers. */
public final class OciResponseUtils {

    public static final String DIGEST = "Docker-Content-Digest";

    /** Why a {@code Location} built by {@link OciRepositoryRequest#path} is no open redirect. */
    public static final String LOCATION = "The Location is a path starting /v2/, then a name that passed the"
            + " distribution grammar and a validated digest or UUID, so it can never leave this origin";

    private OciResponseUtils() {}

    /** A finished upload or mount: {@code 201} with where the blob is. */
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = LOCATION)
    public static void stored(HttpServletResponse response, OciUploadResponse stored) {
        response.setHeader("Location", stored.location());
        response.setHeader(DIGEST, stored.digest().toString());
        empty(response, 201);
    }

    /** An upload in progress, with the range received so far. */
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = LOCATION)
    public static void progress(HttpServletResponse response, OciUploadProgressResponse progress, int status) {
        response.setHeader("Location", progress.location());
        response.setHeader("Docker-Upload-UUID", progress.session().toString());
        response.setHeader("Range", "0-" + Math.max(0, progress.size() - 1));
        empty(response, status);
    }

    public static void empty(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setContentLength(0);
    }

    public static RefusedException unsupported() {
        return new RefusedException(Refusal.of(Reason.UNSUPPORTED, "The operation is unsupported"));
    }
}
