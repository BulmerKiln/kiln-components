/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import java.util.regex.Matcher;

/** Builds the route {@link OciRoute#ROUTE} matched, once its name is valid. */
final class OciRouteKinds {

    private OciRouteKinds() {}

    static OciRoute route(OciRepositoryName name, Matcher match) {
        String route = match.group(2);
        if (match.group(3) != null) {
            return new OciRoute.Manifest(name, OciReference.parse(match.group(3)));
        }
        if ("blobs/uploads/".equals(route)) {
            return new OciRoute.Uploads(name);
        }
        if (match.group(4) != null) {
            return new OciRoute.Upload(name, match.group(4));
        }
        if (match.group(5) != null) {
            return new OciRoute.Blob(name, OciRoute.digest(match.group(5)));
        }
        if ("tags/list".equals(route)) {
            return new OciRoute.Tags(name);
        }
        return new OciRoute.Referrers(name, OciRoute.digest(match.group(6)));
    }
}
