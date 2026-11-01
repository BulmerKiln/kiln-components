/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import com.bulmerkiln.permissions.domain.Ids.TeamId;
import java.util.Objects;
import lombok.Value;

/**
 * Maps one IdP group, from one connector, to a team. The connector is part of the key, so the
 * same group name from two IdPs never means the same thing.
 */
@Value
public final class TeamGroupMapping {

    private final TeamId team;

    private final String connector;

    private final String group;

    public TeamGroupMapping(TeamId team, String connector, String group) {
        Objects.requireNonNull(team, "team");
        if (connector.isBlank() || group.isBlank()) {
            throw new IllegalArgumentException("A group mapping names a connector and a group");
        }
        this.team = team;
        this.connector = connector;
        this.group = group;
    }
}
