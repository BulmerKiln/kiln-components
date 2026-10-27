/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web;

import com.bulmerkiln.permissions.domain.Principal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Who is making a request, from its credentials. The authentication subsystem provides it; until one
 * does, every caller is anonymous (WEB-3).
 */
public interface PrincipalResolver {

    /**
     * The caller, or {@link Principal.Anonymous} when the request carries no credentials. Invalid
     * credentials are a refusal, never a quiet fall back to anonymous.
     */
    Principal resolve(HttpServletRequest request);
}
