/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import com.bulmerkiln.core.digest.Digest;
import lombok.Value;

@Value
public final class PackageFileEntity {

    private final String name;

    private final Digest digest;

    private final long size;
}
