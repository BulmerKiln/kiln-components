/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

/** The referrers index, and whether it was filtered by artifact type. */
public record OciReferrersResponse(String body, boolean filtered) {}
