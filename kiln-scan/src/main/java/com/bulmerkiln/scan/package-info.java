/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The scan subsystem's contract, shared by Kiln's server and the sandboxed scan worker: the
 * versioned job and result messages ({@link com.bulmerkiln.scan.ScanProtocol}), the
 * {@link com.bulmerkiln.scan.ScannerAdapter} every tool's module implements, the rules every
 * adapter runs under ({@link com.bulmerkiln.scan.ScannerAdapters#run}), and
 * {@code ScannerAdapterContract} in this module's test fixtures.
 *
 * <p>It holds no server code, so the worker carries nothing it doesn't need. The worker handles
 * hostile content and may be compromised, so results are untrusted input: every field is bounded
 * and validated. Server-side work lives in kiln-scan-orchestration.
 */
@NullMarked
package com.bulmerkiln.scan;

import org.jspecify.annotations.NullMarked;
