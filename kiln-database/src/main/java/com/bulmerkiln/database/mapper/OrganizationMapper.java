/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.mapper;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.entity.OrganizationEntity;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Rows of {@code organizations} as the kernel's {@link Organization}, built through its validating constructor. */
@Mapper
public interface OrganizationMapper {

    Organization toOrganization(OrganizationEntity entity);

    default OrganizationId toOrganizationId(UUID id) {
        return new OrganizationId(id);
    }
}
