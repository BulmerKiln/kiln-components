/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScannerId;
import com.bulmerkiln.scan.Severity;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One issue after deduplication across scanners.
 *
 * @param id the preferred identifier: a CVE, then a GHSA, then a malware ({@code MAL-}) entry
 * @param aliases every other identifier the scanners gave it
 * @param severity the highest severity any scanner reported
 * @param reportedBy the scanners that found it; fewer than {@code scannersRun} means the engines
 *     disagree, which is shown as "reported by 1 of 3" rather than dropped
 */
public record MergedFinding(
        Finding.Kind kind,
        String id,
        Set<String> aliases,
        @Nullable String purl,
        Severity severity,
        @Nullable String fixedVersion,
        @Nullable String title,
        Set<ScannerId> reportedBy,
        int scannersRun) {

    public MergedFinding {
        aliases = Set.copyOf(aliases);
        reportedBy = Set.copyOf(reportedBy);
    }

    public boolean reportedByAll() {
        return reportedBy.size() >= scannersRun;
    }
}
