/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import com.bulmerkiln.auth.connector.ExternalIdentity;
import com.bulmerkiln.auth.connector.IdentityRules;
import com.bulmerkiln.auth.connector.InvalidIdentityException;
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
                || !issuer.equals(connector.issuer().toString())) {
            throw new InvalidIdentityException("The token's issuer does not match connector " + connector.id());
        }
        // OIDC Core, section 2: sub is a string.
        String subject = IdentityRules.subject(claims.get("sub") instanceof String s ? s : null);
        if (connector.preset().requiresHostedDomain()
                && !(claims.get("hd") instanceof String hostedDomain
                        && connector.domains().contains(hostedDomain))) {
            throw new InvalidIdentityException(
                    "The account is not in a domain connector " + connector.id() + " serves");
        }
        String claim = connector.groupsClaim();
        return new ExternalIdentity(
                connector.id(),
                issuer,
                subject,
                IdentityRules.verifiedEmail(claims.get("email"), claims.get("email_verified")),
                claim == null ? Set.of() : IdentityRules.groups(claims.get(claim)));
    }
}
