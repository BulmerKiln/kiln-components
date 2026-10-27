/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.List;

/** The spec's tag list; records keep the field order. */
public record OciTagListResponse(String name, List<String> tags) {}
