/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

/** The answer to "may this principal do this?". */
public enum Decision {
    ALLOW,
    /** Not signed in: ask for credentials ({@code 401}), whether or not the resource exists. */
    AUTHENTICATE,
    /** Signed in and may know the resource exists, but may not do this ({@code 403}). */
    DENY,
    /** Signed in but may not learn the resource exists ({@code 404}). */
    HIDE
}
