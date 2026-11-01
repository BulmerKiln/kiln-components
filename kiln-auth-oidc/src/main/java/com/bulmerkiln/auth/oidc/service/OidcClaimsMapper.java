/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.service;

import com.bulmerkiln.auth.connector.InvalidIdentityException;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.util.IdentityUtils;
import com.bulmerkiln.auth.oidc.OidcConnector;
import java.util.Map;
import java.util.Set;

/**
 * Turns the claims of an ID token into an identity. The token's signature, audience, expiry and
 * nonce are checked by Spring Security before this runs; this applies Kiln's own rules on top.
 */
public final class OidcClaimsMapper {

    private OidcClaimsMapper() {}

    public static ExternalIdentity map(OidcConnector connector, Map<String, ?> claims) {
        // OIDC Core, section 3.1.3.7: the issuer must match exactly, with no normalization.
        if (!(claims.get("iss") instanceof String issuer)
                || !issuer.equals(connector.getIssuer().toString())) {
            throw new InvalidIdentityException("The token's issuer does not match connector " + connector.getId());
        }
        // OIDC Core, section 2: sub is a string.
        String subject = IdentityUtils.subject(claims.get("sub") instanceof String s ? s : null);
        if (connector.getPreset().requiresHostedDomain()
                && !(claims.get("hd") instanceof String hostedDomain
                        && connector.getDomains().contains(hostedDomain))) {
            throw new InvalidIdentityException(
                    "The account is not in a domain connector " + connector.getId() + " serves");
        }
        String claim = connector.getGroupsClaim();
        return new ExternalIdentity(
                connector.getId(),
                issuer,
                subject,
                IdentityUtils.verifiedEmail(claims.get("email"), claims.get("email_verified")),
                claim == null ? Set.of() : IdentityUtils.groups(claims.get(claim)));
    }
}
