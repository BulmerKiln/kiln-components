/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One scanner's result for a job.
 *
 * @param databaseVersion the exact database the scanner matched against, recorded with every result
 * @param incompleteReason required when {@code status} is {@code INCOMPLETE}, such as a limit hit
 */
public record ScanResult(
        int protocol,
        UUID jobId,
        ScannerId scanner,
        String scannerVersion,
        String databaseVersion,
        Status status,
        @Nullable String incompleteReason,
        List<Finding> findings) {

    public static final int MAX_FINDINGS = 100_000;

    public enum Status {
        COMPLETE,
        /** The scan stopped early, for example at an archive limit; never counted as clean. */
        INCOMPLETE,
        FAILED
    }

    public ScanResult {
        ScanProtocol.requireVersion(protocol);
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(scanner, "scanner");
        Objects.requireNonNull(status, "status");
        Bounds.text(scannerVersion, "scannerVersion", 128);
        Bounds.text(databaseVersion, "databaseVersion", 255);
        findings = List.copyOf(findings);
        if (findings.size() > MAX_FINDINGS) {
            throw new IllegalArgumentException("More than " + MAX_FINDINGS + " findings");
        }
        if (status == Status.INCOMPLETE) {
            Bounds.text(Objects.requireNonNullElse(incompleteReason, ""), "incompleteReason", 512);
        }
        if (status == Status.FAILED && !findings.isEmpty()) {
            throw new IllegalArgumentException("A failed scan has no findings");
        }
    }
}
