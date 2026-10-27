/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.s3;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.storage.UploadId;
import java.util.regex.Pattern;

/**
 * Object keys, laid out as the filesystem store lays out paths, so blobs copy across unchanged:
 * {@code [prefix/]<organization-id>/blobs/sha256/<ab>/<hex>} and {@code [prefix/]<organization-id>/_uploads/<uuid>}.
 * Every part comes from a validated type, never from client text.
 */
public final class S3Keys {

    private static final Pattern SLASH = Pattern.compile("/");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final String prefix;

    /** @param prefix a key prefix such as {@code kiln/prod}, or empty to use the bucket root */
    public S3Keys(String prefix) {
        if (!prefix.isEmpty() && (prefix.length() > 256 || !isPrefix(prefix))) {
            throw new IllegalArgumentException("Invalid key prefix: " + prefix);
        }
        this.prefix = prefix.isEmpty() ? "" : prefix + "/";
    }

    /** Lowercase segments joined by single slashes. */
    private static boolean isPrefix(String prefix) {
        for (String segment : SLASH.split(prefix, -1)) {
            if (!SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    public String blob(OrganizationId organization, Digest digest) {
        String hex = digest.encoded();
        return prefix + organization + "/blobs/" + digest.algorithm().id() + "/" + hex.substring(0, 2) + "/" + hex;
    }

    public String upload(OrganizationId organization, UploadId upload) {
        return prefix + organization + "/_uploads/" + upload;
    }

    /** Everything an organization owns, for listing and for deleting the organization. */
    public String organization(OrganizationId organization) {
        return prefix + organization + "/";
    }
}
