/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

/** Something a principal may be allowed to do. */
public enum Action {
    PULL,
    /** Push and publish. */
    PUSH,
    DELETE,
    BLOCK,
    UNBLOCK,
    /** Findings, the impact report and the security posture. */
    VIEW_SECURITY,
    /** Policies, members, teams, role bindings and connectors. */
    MANAGE
}
