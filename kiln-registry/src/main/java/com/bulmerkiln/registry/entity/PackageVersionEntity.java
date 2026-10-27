/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.time.Instant;
import java.util.UUID;

public record PackageVersionEntity(UUID id, String version, String metadata, boolean yanked, Instant publishedAt) {}
