/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * One scanner tool, as kiln-scanner-service runs it. Running and parsing are separate so parsing
 * can be tested against recorded tool output. Every adapter must pass {@code ScannerAdapterContract},
 * and the worker always calls it through {@link ScannerAdapters#run}, which applies the rules every
 * scanner shares.
 */
public interface ScannerAdapter {

    ScannerId scanner();

    /**
     * The tool's command line, run directly, never through a shell. It reads the artifact from
     * {@code input}, a local copy in the job's workspace; the presigned blob URL never reaches the tool.
     */
    List<String> command(ScanJob job, Path input);

    /**
     * Whether this adapter reads the output of {@code toolVersion}, as the tool reports it. The
     * worker refuses to start with a bundled tool outside the range, so an upgrade that changes the
     * output can't produce wrong results.
     */
    boolean supportsVersion(String toolVersion);

    /** Whether the tool's exit code means it ran to completion; most tools use 0. */
    default boolean completed(int exitCode) {
        return exitCode == 0;
    }

    /**
     * Turns the tool's output into findings. It may throw on output it can't read; the caller turns
     * that into a failed result. It must never put a secret's value into a finding.
     */
    List<Finding> parse(ScanJob job, ToolRun run);
}
