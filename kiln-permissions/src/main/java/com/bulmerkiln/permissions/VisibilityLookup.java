/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

/**
 * Whether a namespace, repository or package is public. Implemented by the namespace data layer;
 * no default, so nothing becomes public by accident.
 */
public interface VisibilityLookup {

    Visibility of(Scope scope);
}
