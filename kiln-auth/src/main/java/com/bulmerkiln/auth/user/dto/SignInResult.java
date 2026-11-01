/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.dto;

/** What a sign-in came to: a user, or a refusal. */
public sealed interface SignInResult permits SignedInResponse, SignInRefusedResponse {}
