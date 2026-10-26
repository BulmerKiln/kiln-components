/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

/**
 * Receives every push and pull, from every format. Subsystems implement it: metrics now, and the
 * audit log, events outbox and usage counters as they arrive. A listener that must not lose an
 * event, such as the audit log, throws to fail the request; one that's best effort, such as
 * metrics, never throws.
 */
public interface ArtifactEventListener {

    void pushed(ArtifactRef artifact, long bytes);

    void pulled(ArtifactRef artifact, long bytes);
}
