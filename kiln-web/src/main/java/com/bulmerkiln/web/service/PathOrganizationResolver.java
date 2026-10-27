/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.web.service;

import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationResolver;
import java.util.Optional;

/**
 * The organization is the first segment of the artifact path: {@code acme/app} is {@code app} in
 * organization {@code acme} (WEB-6). The slug is looked up exactly as sent; anything that isn't an
 * active organization's slug resolves to none.
 */
public final class PathOrganizationResolver implements OrganizationResolver {

    private final OrganizationDirectory directory;

    public PathOrganizationResolver(OrganizationDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Optional<Resolution> resolve(String path) {
        int slash = path.indexOf('/');
        String slug = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash + 1);
        return directory.bySlug(slug).map(organization -> new Resolution(organization, rest));
    }
}
