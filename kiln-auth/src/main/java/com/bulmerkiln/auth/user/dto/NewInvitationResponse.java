/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.dto;

import com.bulmerkiln.auth.token.OpaqueSecret;
import com.bulmerkiln.auth.user.entity.InvitationEntity;
import lombok.Value;

/** A new invitation, with the token for its link: shown to the admin once, then only its hash remains. */
@Value
public final class NewInvitationResponse {

    private final OpaqueSecret token;

    private final InvitationEntity invitation;
}
