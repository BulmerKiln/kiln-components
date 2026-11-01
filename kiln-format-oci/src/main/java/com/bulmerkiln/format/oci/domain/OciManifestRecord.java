/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/** What the database knows of a stored manifest; its bytes are a blob with the same digest. */
@Value
public final class OciManifestRecord {

    private final Digest digest;

    private final String mediaType;

    private final @Nullable String artifactType;

    private final long size;
}
