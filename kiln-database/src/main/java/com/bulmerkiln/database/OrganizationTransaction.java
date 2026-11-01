/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.Organization;
import java.io.Serial;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/** A transaction for one organization: {@link OrganizationTransactionManager} binds it to the connection. */
final class OrganizationTransaction extends DefaultTransactionDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Organization organization;

    OrganizationTransaction(Organization organization) {
        this.organization = organization;
        setName("organization " + organization.getSlug());
    }

    Organization organization() {
        return organization;
    }
}
