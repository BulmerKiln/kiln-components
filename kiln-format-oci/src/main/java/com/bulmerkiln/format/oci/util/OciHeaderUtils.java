/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.util;

import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Reads the request headers and parameters the upload and tag routes take (FMT-OCI-29). */
public final class OciHeaderUtils {

    /** OCI's chunk range, {@code <start>-<end>}, without HTTP's {@code bytes} unit. */
    private static final Pattern CONTENT_RANGE = Pattern.compile("([0-9]{1,18})-([0-9]{1,18})");

    private static final Pattern PAGE_SIZE = Pattern.compile("[0-9]{1,9}");

    private OciHeaderUtils() {}

    /** Where a chunk starts, or null when the client sent no range and appends at the end. */
    public static @Nullable Long contentRangeStart(@Nullable String header) {
        if (header == null) {
            return null;
        }
        Matcher match = CONTENT_RANGE.matcher(header);
        if (!match.matches() || Long.parseLong(match.group(2)) < Long.parseLong(match.group(1))) {
            throw new OciException(OciErrorCode.BLOB_UPLOAD_INVALID, "Invalid Content-Range");
        }
        return Long.parseLong(match.group(1));
    }

    /** The {@code n} parameter: a page size, or null to use the default. */
    public static @Nullable Integer pageSize(@Nullable String n) {
        if (n == null) {
            return null;
        }
        if (!PAGE_SIZE.matcher(n).matches()) {
            throw new OciException(OciErrorCode.BLOB_UPLOAD_INVALID, "Invalid page size");
        }
        return Integer.parseInt(n);
    }
}
