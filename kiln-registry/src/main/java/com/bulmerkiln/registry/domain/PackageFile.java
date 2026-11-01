/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.core.digest.Digest;
import java.util.UUID;
import lombok.Value;

/** A file of a version, and the blob holding its bytes. */
@Value
public final class PackageFile {

    private final UUID id;

    private final String name;

    private final Digest digest;

    private final long size;
}
