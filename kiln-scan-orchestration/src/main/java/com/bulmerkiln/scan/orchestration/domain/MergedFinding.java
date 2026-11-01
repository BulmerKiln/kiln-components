/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration.domain;

import com.bulmerkiln.scan.constant.Severity;
import com.bulmerkiln.scan.domain.Finding;
import com.bulmerkiln.scan.domain.ScannerId;
import java.util.Set;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One issue after deduplication across scanners.
 */
@Value
public final class MergedFinding {

    private final Finding.Kind kind;

    /** The preferred identifier: a CVE, then a GHSA, then a malware ({@code MAL-}) entry. */
    private final String id;

    /** Every other identifier the scanners gave it. */
    private final Set<String> aliases;

    private final @Nullable String purl;

    /** The highest severity any scanner reported. */
    private final Severity severity;

    private final @Nullable String fixedVersion;

    private final @Nullable String title;

    /**
     * The scanners that found it; fewer than {@code scannersRun} means the engines disagree, which is
     * shown as "reported by 1 of 3" rather than dropped.
     */
    private final Set<ScannerId> reportedBy;

    private final int scannersRun;

    public MergedFinding(
            Finding.Kind kind,
            String id,
            Set<String> aliases,
            @Nullable String purl,
            Severity severity,
            @Nullable String fixedVersion,
            @Nullable String title,
            Set<ScannerId> reportedBy,
            int scannersRun) {
        this.aliases = Set.copyOf(aliases);
        this.reportedBy = Set.copyOf(reportedBy);
        this.kind = kind;
        this.id = id;
        this.purl = purl;
        this.severity = severity;
        this.fixedVersion = fixedVersion;
        this.title = title;
        this.scannersRun = scannersRun;
    }

    public boolean reportedByAll() {
        return reportedBy.size() >= scannersRun;
    }
}
