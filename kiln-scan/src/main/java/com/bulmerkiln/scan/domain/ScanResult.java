/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.domain;

import com.bulmerkiln.scan.ScanProtocol;
import com.bulmerkiln.scan.util.BoundsUtils;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One scanner's result for a job.
 */
@Value
public final class ScanResult {

    private final int protocol;

    private final UUID jobId;

    private final ScannerId scanner;

    private final String scannerVersion;

    /** The exact database the scanner matched against, recorded with every result. */
    private final String databaseVersion;

    private final Status status;

    /** Required when {@code status} is {@code INCOMPLETE}, such as a limit hit. */
    private final @Nullable String incompleteReason;

    private final List<Finding> findings;

    public static final int MAX_FINDINGS = 100_000;

    public enum Status {
        COMPLETE,
        /** The scan stopped early, for example at an archive limit; never counted as clean. */
        INCOMPLETE,
        FAILED
    }

    public ScanResult(
            int protocol,
            UUID jobId,
            ScannerId scanner,
            String scannerVersion,
            String databaseVersion,
            Status status,
            @Nullable String incompleteReason,
            List<Finding> findings) {
        ScanProtocol.requireVersion(protocol);
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(scanner, "scanner");
        Objects.requireNonNull(status, "status");
        BoundsUtils.text(scannerVersion, "scannerVersion", 128);
        BoundsUtils.text(databaseVersion, "databaseVersion", 255);
        this.findings = List.copyOf(findings);
        if (this.findings.size() > MAX_FINDINGS) {
            throw new IllegalArgumentException("More than " + MAX_FINDINGS + " findings");
        }
        if (status == Status.INCOMPLETE) {
            BoundsUtils.text(Objects.requireNonNullElse(incompleteReason, ""), "incompleteReason", 512);
        }
        if (status == Status.FAILED && !this.findings.isEmpty()) {
            throw new IllegalArgumentException("A failed scan has no findings");
        }
        this.protocol = protocol;
        this.jobId = jobId;
        this.scanner = scanner;
        this.scannerVersion = scannerVersion;
        this.databaseVersion = databaseVersion;
        this.status = status;
        this.incompleteReason = incompleteReason;
    }
}
