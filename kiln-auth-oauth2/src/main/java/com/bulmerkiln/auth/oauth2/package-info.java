/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The generic OAuth2 connector (RFC 6749, authorization code with PKCE) for IdPs without OIDC,
 * such as GitHub. Identity comes from the user-info endpoint, read through configured paths.
 */
@NullMarked
package com.bulmerkiln.auth.oauth2;

import org.jspecify.annotations.NullMarked;
