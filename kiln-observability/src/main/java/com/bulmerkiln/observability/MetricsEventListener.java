/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.ArtifactRef;

/** Counts every format's pushes, pulls and bytes. Best effort: it never fails a request. */
public final class MetricsEventListener implements ArtifactEventListener {

    private final KilnMetrics metrics;

    public MetricsEventListener(KilnMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void pushed(ArtifactRef artifact, long bytes) {
        metrics.push(artifact.format(), artifact.namespace());
        metrics.bytesReceived(artifact.format(), artifact.namespace(), bytes);
    }

    @Override
    public void pulled(ArtifactRef artifact, long bytes) {
        metrics.pull(artifact.format(), artifact.namespace());
        metrics.bytesServed(artifact.format(), artifact.namespace(), bytes);
    }
}
