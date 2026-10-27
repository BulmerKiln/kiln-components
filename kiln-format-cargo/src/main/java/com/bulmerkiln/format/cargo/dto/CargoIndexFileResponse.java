/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.dto;

import java.time.Instant;

/** An index file: one JSON line per version a block doesn't refuse, and when it last changed. */
public record CargoIndexFileResponse(String body, Instant modified) {}
