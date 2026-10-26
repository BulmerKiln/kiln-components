/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.s3;

/** {@code storage.redirect}: when blob downloads redirect to a presigned URL instead of being proxied. */
public enum RedirectMode {
    /** Redirect only the clients known to follow it without forwarding {@code Authorization}. */
    AUTO,
    ALWAYS,
    NEVER
}
