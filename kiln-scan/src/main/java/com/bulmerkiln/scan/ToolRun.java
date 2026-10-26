/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What running a scanner tool in the sandbox produced.
 *
 * @param output the tool's report, as the worker read it; untrusted and possibly truncated
 * @param timedOut whether the worker stopped the tool at the job's timeout
 * @param limitHit the archive limit the tool stopped at, such as {@code max files}, if any
 */
public record ToolRun(
        String toolVersion,
        String databaseVersion,
        int exitCode,
        String output,
        boolean timedOut,
        @Nullable String limitHit) {

    public ToolRun {
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(databaseVersion, "databaseVersion");
        Objects.requireNonNull(output, "output");
    }
}
