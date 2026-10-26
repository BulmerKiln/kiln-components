/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Scan orchestration, run by Kiln's server and never by the worker: merging and deduplicating
 * findings across scanners now, and job dispatch, EPSS and KEV enrichment, VEX exceptions and the
 * outputs into policy and notifications in M4.
 */
@NullMarked
package com.bulmerkiln.scan.orchestration;

import org.jspecify.annotations.NullMarked;
