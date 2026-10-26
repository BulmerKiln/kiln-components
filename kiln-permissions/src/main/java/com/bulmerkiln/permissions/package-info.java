/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The permissions subsystem: what a principal may do. It never
 * asks how someone signed in. Authentication (kiln-auth) produces a
 * {@link com.bulmerkiln.permissions.Principal}; this module decides with a
 * {@link com.bulmerkiln.permissions.PermissionPolicy}, which every implementation proves against
 * {@code PermissionPolicyContract}, the authorization matrix.
 *
 * <p>Nothing is granted by default. A member's teams come from IdP groups and explicit
 * membership; with neither, they have no roles.
 */
@NullMarked
package com.bulmerkiln.permissions;

import org.jspecify.annotations.NullMarked;
