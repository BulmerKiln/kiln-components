/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.dto;

import java.time.Instant;
import lombok.Value;

/** An index file: one JSON line per version a block doesn't refuse, and when it last changed. */
@Value
public final class CargoIndexFileResponse {

    private final String body;

    private final Instant modified;
}
