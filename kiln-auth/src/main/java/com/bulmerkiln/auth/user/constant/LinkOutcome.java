/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

/** What linking a further identity to a signed-in user did. */
public enum LinkOutcome {
    LINKED,
    /** The identity was already this user's. */
    ALREADY_LINKED,
    /** The session's sign-in is more than 10 minutes old: sign in again first. */
    SIGN_IN_TOO_OLD,
    /** Another user holds the identity. Never moved between users. */
    HELD_BY_ANOTHER_USER,
    /** The user is disabled. */
    USER_DISABLED
}
