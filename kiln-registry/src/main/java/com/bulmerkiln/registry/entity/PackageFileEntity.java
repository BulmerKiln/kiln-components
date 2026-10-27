/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import com.bulmerkiln.core.digest.Digest;

public record PackageFileEntity(String name, Digest digest, long size) {}
