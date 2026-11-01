/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.InvalidDigestException;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A distribution-spec route below {@code /v2/}. Repository names may contain {@code /}, and their
 * components may even be {@code blobs} or {@code manifests}, so a route is read from the end of the
 * path: the last route keyword decides, and everything before it is the name (FMT-OCI-14).
 */
public sealed interface OciRoute {

    OciRepositoryName getRepository();

    @Value
    static final class Manifest implements OciRoute {

        private final OciRepositoryName repository;

        private final OciReference reference;
    }

    @Value
    static final class Blob implements OciRoute {

        private final OciRepositoryName repository;

        private final Digest digest;
    }

    /** {@code blobs/uploads/}: starting an upload or a mount. */
    @Value
    static final class Uploads implements OciRoute {

        private final OciRepositoryName repository;
    }

    /** {@code blobs/uploads/<session>}: the session ID as given, checked when it's looked up. */
    @Value
    static final class Upload implements OciRoute {

        private final OciRepositoryName repository;

        private final String session;
    }

    @Value
    static final class Tags implements OciRoute {

        private final OciRepositoryName repository;
    }

    @Value
    static final class Referrers implements OciRoute {

        private final OciRepositoryName repository;

        private final Digest digest;
    }

    /** Longer than any valid name plus a route; refused before matching. */
    int MAX_LENGTH = OciRepositoryName.MAX_LENGTH + 256;

    /** Name, then the route. Names never contain {@code //} or end in {@code /}. */
    Pattern ROUTE = Pattern.compile(
            "(.+?)/(manifests/([^/]+)|blobs/uploads/|blobs/uploads/([^/]+)|blobs/([^/]+)|tags/list|referrers/([^/]+))");

    /**
     * Parses the path below {@code /v2/}, without a leading slash. A path that's no route is {@code
     * NOT_FOUND}; a malformed name, tag or digest is the spec's error for it.
     */
    static OciRoute parse(String path) {
        if (path.length() > MAX_LENGTH) {
            throw new OciException(OciErrorCode.NAME_INVALID, "Invalid repository name");
        }
        Matcher match = ROUTE.matcher(path);
        if (!match.matches()) {
            throw new RefusedException(Refusal.of(Reason.NOT_FOUND, "Not found"));
        }
        return OciRouteKinds.route(new OciRepositoryName(match.group(1)), match);
    }

    /** A digest in a path; malformed or unsupported is {@code DIGEST_INVALID}. */
    static Digest digest(String value) {
        try {
            return Digest.parse(value);
        } catch (InvalidDigestException e) {
            throw new OciException(OciErrorCode.DIGEST_INVALID, "Invalid digest");
        }
    }
}
