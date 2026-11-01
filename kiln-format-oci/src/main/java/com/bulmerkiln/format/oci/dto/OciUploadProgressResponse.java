/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.UUID;
import lombok.Value;

/** Where an upload stands: its URL and how many bytes it has. */
@Value
public final class OciUploadProgressResponse {

    private final String location;

    private final UUID session;

    private final long size;
}
