/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.List;

/**
 * Where role bindings are stored, in the current organization. Implemented by the data layer;
 * there's no default, so an assembly without one fails to start rather than granting nothing
 * silently or everything by mistake.
 */
public interface RoleBindingRepository {

    /** The bindings that may apply to {@code principal}. Extra ones are harmless: each is rechecked. */
    List<RoleBinding> bindingsFor(Principal principal);
}
