/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.storage.domain.BlobInfo;
import lombok.Value;

/** A file's bytes as stored, and every checksum Maven asks for, taken while they streamed in. */
@Value
public final class MavenDeployedFile {

    private final BlobInfo blob;

    private final BlobChecksums checksums;
}
