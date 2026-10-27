/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.service;

import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.web.PrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;

/** Every caller is anonymous, so only public pulls are allowed: the default until sign-in exists. */
public final class AnonymousPrincipalResolver implements PrincipalResolver {

    @Override
    public Principal resolve(HttpServletRequest request) {
        return new Principal.Anonymous();
    }
}
