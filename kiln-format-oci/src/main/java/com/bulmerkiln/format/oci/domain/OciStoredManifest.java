/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import lombok.Value;

/** A stored manifest and its bytes exactly as pushed, read back from the blob store for a pull. */
@Value
public final class OciStoredManifest {

    private final OciManifestRecord record;

    private final byte[] content;
}
