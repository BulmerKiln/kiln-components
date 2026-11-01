/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

/**
 * Why a sign-in was refused. For the log only: the person sees one page whatever the reason, so it
 * never says whether their email is known.
 */
public enum SignInRefusal {
    /** No user has this identity, and no invitation, bootstrap entry or allowed domain admits it. */
    UNKNOWN_IDENTITY,
    /** The user is disabled. */
    USER_DISABLED,
    /** The invite link is unknown, expired or already used. */
    INVITATION_INVALID,
    /** The email already created a user, through another identity; it's never matched twice. */
    EMAIL_ALREADY_CLAIMED
}
