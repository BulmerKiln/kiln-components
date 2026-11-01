/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.dto;

import com.bulmerkiln.auth.user.constant.SignInRoute;
import com.bulmerkiln.auth.user.entity.UserEntity;
import lombok.Value;

/** The identity signed in as {@code user}, found by {@code route}. */
@Value
public final class SignedInResponse implements SignInResult {

    private final UserEntity user;

    private final SignInRoute route;
}
