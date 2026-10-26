/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import java.util.List;

/**
 * What formats report pushes and pulls to. It delivers each event to every
 * {@link ArtifactEventListener}, so formats never know who's listening. With no listener at all
 * there's no {@code ArtifactEvents}, and an assembly with formats fails to start rather than
 * dropping events silently.
 */
public final class ArtifactEvents {

    private final List<ArtifactEventListener> listeners;

    public ArtifactEvents(List<ArtifactEventListener> listeners) {
        if (listeners.isEmpty()) {
            throw new IllegalArgumentException("ArtifactEvents needs at least one listener");
        }
        this.listeners = List.copyOf(listeners);
    }

    /** Reports a push; a listener that throws fails it, after the listeners before it ran. */
    public void pushed(ArtifactRef artifact, long bytes) {
        requireBytes(bytes);
        for (ArtifactEventListener listener : listeners) {
            listener.pushed(artifact, bytes);
        }
    }

    public void pulled(ArtifactRef artifact, long bytes) {
        requireBytes(bytes);
        for (ArtifactEventListener listener : listeners) {
            listener.pulled(artifact, bytes);
        }
    }

    private static void requireBytes(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Byte counts are never negative");
        }
    }
}
