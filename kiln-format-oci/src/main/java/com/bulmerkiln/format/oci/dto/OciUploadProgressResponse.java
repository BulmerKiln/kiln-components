/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import java.util.UUID;

/** Where an upload stands: its URL and how many bytes it has. */
public record OciUploadProgressResponse(String location, UUID session, long size) {}
