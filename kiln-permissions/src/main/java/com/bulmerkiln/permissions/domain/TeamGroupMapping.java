/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import com.bulmerkiln.permissions.domain.Ids.TeamId;
import java.util.Objects;

/**
 * Maps one IdP group, from one connector, to a team. The connector is part of the key, so the
 * same group name from two IdPs never means the same thing.
 */
public record TeamGroupMapping(TeamId team, String connector, String group) {
    public TeamGroupMapping {
        Objects.requireNonNull(team, "team");
        if (connector.isBlank() || group.isBlank()) {
            throw new IllegalArgumentException("A group mapping names a connector and a group");
        }
    }
}
