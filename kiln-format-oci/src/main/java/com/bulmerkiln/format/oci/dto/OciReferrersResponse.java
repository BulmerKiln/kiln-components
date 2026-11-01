/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import lombok.Value;

/** The referrers index, and whether it was filtered by artifact type. */
@Value
public final class OciReferrersResponse {

    private final String body;

    private final boolean filtered;
}
