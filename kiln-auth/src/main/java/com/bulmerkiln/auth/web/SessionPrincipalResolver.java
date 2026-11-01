/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.web;

import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.web.PrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the caller from the web session established by Spring Security's OAuth2 login. Reads the
 * member ID stored at sign-in; returns anonymous when there is no session.
 */
public final class SessionPrincipalResolver implements PrincipalResolver {

    @Override
    public Principal resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new Principal.Anonymous();
        }
        String memberId = (String) session.getAttribute(SecurityAutoConfiguration.SESSION_USER_KEY);
        if (memberId == null) {
            return new Principal.Anonymous();
        }
        try {
            return new Principal.Member(new MemberId(UUID.fromString(memberId)), Set.of());
        } catch (IllegalArgumentException e) {
            return new Principal.Anonymous();
        }
    }
}
