/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

/** How a sign-in found its user, in the order they're tried. */
public enum SignInRoute {
    /** An identity the user already has. */
    KNOWN_IDENTITY,
    /** An invite link, claimed by this identity. */
    INVITATION,
    /** An issuer and subject in {@code kiln.bootstrap.admin-subjects}. */
    BOOTSTRAP_SUBJECT,
    /** A verified email in {@code kiln.bootstrap.admin-emails}, through a connector that allows its domain. */
    BOOTSTRAP_EMAIL,
    /** A verified email at an allowed domain, through that domain's connector. */
    ALLOWED_DOMAIN
}
