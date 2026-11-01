/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;

/** A finished upload, or a mount: the blob's URL and digest. */
@Value
public final class OciUploadResponse {

    private final String location;

    private final Digest digest;
}
