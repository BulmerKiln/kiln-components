/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.util;

import java.util.Locale;

/** Emails as Kiln matches them: only ones an IdP verified, compared without case. */
public final class EmailUtils {

    private EmailUtils() {}

    /** The domain part, lowercased, for matching an allowed domain exactly. */
    public static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    }
}
