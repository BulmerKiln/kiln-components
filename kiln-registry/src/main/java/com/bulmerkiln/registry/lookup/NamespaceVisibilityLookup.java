/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.lookup;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.service.NamespaceService;

/**
 * Visibility comes from the namespace. The organization itself, and a namespace that doesn't
 * exist, are private, so nothing becomes public by accident (REG-5).
 */
public final class NamespaceVisibilityLookup implements Lookup<Scope, Visibility> {

    private final Database database;
    private final NamespaceService namespaces;

    public NamespaceVisibilityLookup(Database database, NamespaceService namespaces) {
        this.database = database;
        this.namespaces = namespaces;
    }

    @Override
    public Visibility of(Scope scope) {
        String namespace = scope.getNamespace();
        if (namespace == null) {
            return Visibility.PRIVATE;
        }
        return database.transaction(
                () -> namespaces.byName(namespace).map(Namespace::getVisibility).orElse(Visibility.PRIVATE));
    }
}
