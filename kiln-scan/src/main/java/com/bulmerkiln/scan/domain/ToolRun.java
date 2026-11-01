/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * What running a scanner tool in the sandbox produced.
 */
@Value
public final class ToolRun {

    private final String toolVersion;

    private final String databaseVersion;

    private final int exitCode;

    /** The tool's report, as the worker read it; untrusted and possibly truncated. */
    private final String output;

    /** Whether the worker stopped the tool at the job's timeout. */
    private final boolean timedOut;

    /** The archive limit the tool stopped at, such as {@code max files}, if any. */
    private final @Nullable String limitHit;

    public ToolRun(
            String toolVersion,
            String databaseVersion,
            int exitCode,
            String output,
            boolean timedOut,
            @Nullable String limitHit) {
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(databaseVersion, "databaseVersion");
        Objects.requireNonNull(output, "output");
        this.toolVersion = toolVersion;
        this.databaseVersion = databaseVersion;
        this.exitCode = exitCode;
        this.output = output;
        this.timedOut = timedOut;
        this.limitHit = limitHit;
    }
}
