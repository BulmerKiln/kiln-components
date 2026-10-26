/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.List;

/**
 * Runs an adapter's parsing under the rules every scanner shares, so no adapter can turn a failed
 * or partial scan into a clean pass (SCAN-6 to SCAN-8).
 */
public final class ScannerAdapters {

    private ScannerAdapters() {}

    /**
     * The result for one tool run.
     *
     * <ul>
     *   <li>A tool that didn't complete, or output the adapter can't parse, gives {@code FAILED}
     *       with no findings.
     *   <li>A timeout or an archive limit gives {@code INCOMPLETE}, keeping what was found.
     *   <li>Findings beyond the protocol's limits give {@code FAILED}, never a truncated pass.
     * </ul>
     */
    public static ScanResult run(ScannerAdapter adapter, ScanJob job, ToolRun run) {
        String version = printable(run.toolVersion(), 128);
        String database = printable(run.databaseVersion(), 255);
        if (!run.timedOut() && run.limitHit() == null && !adapter.completed(run.exitCode())) {
            return failed(adapter, job, version, database);
        }
        List<Finding> findings;
        try {
            findings = List.copyOf(adapter.parse(job, run));
        } catch (RuntimeException | StackOverflowError e) {
            return failed(adapter, job, version, database);
        }
        if (findings.size() > ScanResult.MAX_FINDINGS) {
            return failed(adapter, job, version, database);
        }
        String limitHit = run.limitHit();
        if (run.timedOut() || limitHit != null) {
            String reason = limitHit == null ? "timed out" : printable(limitHit, 512);
            return new ScanResult(
                    ScanProtocol.VERSION,
                    job.jobId(),
                    adapter.scanner(),
                    version,
                    database,
                    ScanResult.Status.INCOMPLETE,
                    reason,
                    findings);
        }
        return new ScanResult(
                ScanProtocol.VERSION,
                job.jobId(),
                adapter.scanner(),
                version,
                database,
                ScanResult.Status.COMPLETE,
                null,
                findings);
    }

    private static ScanResult failed(ScannerAdapter adapter, ScanJob job, String version, String database) {
        return new ScanResult(
                ScanProtocol.VERSION,
                job.jobId(),
                adapter.scanner(),
                version,
                database,
                ScanResult.Status.FAILED,
                null,
                List.of());
    }

    /** Tool-reported text, made safe for the protocol's bounds; {@code unknown} if nothing is left. */
    private static String printable(String value, int max) {
        String cleaned = ToolText.clean(value, max);
        return cleaned == null ? "unknown" : cleaned;
    }
}
