/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;
import lombok.Value;

/** The spec's tag list; records keep the field order. */
@Value
public final class OciTagListResponse {

    private final String name;

    private final List<String> tags;
}
