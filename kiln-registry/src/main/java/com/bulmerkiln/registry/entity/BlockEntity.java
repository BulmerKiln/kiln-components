/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A row of {@code blocks} as one is set: what it targets ({@code selectorKind} {@code version},
 * {@code range}, {@code digest} or {@code package}), why, where it came from ({@code source}
 * {@code manual}, {@code malware_feed} or {@code scanner_policy}), and who set a manual one.
 */
@Value
@Table("blocks")
public final class BlockEntity {

    @Id
    private final RegistryKey id;

    private final String format;

    private final @Nullable String name;

    private final String selectorKind;

    private final String selector;

    private final String reason;

    private final String source;

    private final @Nullable String createdByKind;

    private final @Nullable UUID createdById;
}
