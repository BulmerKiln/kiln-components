/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.orchestration.domain;

import com.bulmerkiln.scan.constant.Severity;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;

/** Findings per severity, as stored in {@code scan_results} and shown per artifact and organization. */
@Value
public final class SeverityCounts {

    private final Map<Severity, Integer> counts;

    public SeverityCounts(Map<Severity, Integer> counts) {
        Map<Severity, Integer> complete = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            complete.put(severity, counts.getOrDefault(severity, 0));
        }
        this.counts = Map.copyOf(complete);
    }

    public static SeverityCounts of(Collection<MergedFinding> findings) {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (MergedFinding finding : findings) {
            counts.merge(finding.getSeverity(), 1, Integer::sum);
        }
        return new SeverityCounts(counts);
    }

    public int get(Severity severity) {
        return counts.getOrDefault(severity, 0);
    }
}
