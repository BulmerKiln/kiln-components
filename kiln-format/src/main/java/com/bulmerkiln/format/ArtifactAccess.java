/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

/**
 * The port formats ask whether the current caller may do something to an artifact. The
 * permissions subsystem implements it; like the other ports it has no allow-all default.
 */
public interface ArtifactAccess {

    Access check(ArtifactRef artifact, Operation operation);
}
