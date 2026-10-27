/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;

/** A package, by the name it was first published under. */
public record PackageEntity(UUID id, UUID namespaceId, String name, String metadata) {}
