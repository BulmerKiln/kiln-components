/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.dto;

import com.bulmerkiln.auth.user.constant.SignInRefusal;
import lombok.Value;

/** The sign-in was refused. The reason is for the log, never for the person signing in. */
@Value
public final class SignInRefusedResponse implements SignInResult {

    private final SignInRefusal reason;
}
