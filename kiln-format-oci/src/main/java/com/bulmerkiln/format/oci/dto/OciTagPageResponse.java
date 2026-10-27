/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import org.jspecify.annotations.Nullable;

/** One page of tags: the JSON body, and the next page's URL when there is one. */
public record OciTagPageResponse(String body, @Nullable String next) {}
