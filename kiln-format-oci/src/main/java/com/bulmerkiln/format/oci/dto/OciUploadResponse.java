/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.dto;

import com.bulmerkiln.core.digest.Digest;

/** A finished upload, or a mount: the blob's URL and digest. */
public record OciUploadResponse(String location, Digest digest) {}
