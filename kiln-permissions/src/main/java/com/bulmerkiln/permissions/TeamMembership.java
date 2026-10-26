/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import com.bulmerkiln.permissions.Ids.TeamId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Turns the groups an IdP reported at sign-in into teams. Recomputed at every sign-in, so a group
 * removed at the IdP takes its teams with it. No groups, or none mapped, leaves only explicit
 * teams: least privilege.
 */
public final class TeamMembership {

    /**
     * Maps one IdP group, from one connector, to a team. The connector is part of the key, so the
     * same group name from two IdPs never means the same thing.
     */
    public record GroupMapping(TeamId team, String connector, String group) {
        public GroupMapping {
            Objects.requireNonNull(team, "team");
            if (connector.isBlank() || group.isBlank()) {
                throw new IllegalArgumentException("A group mapping names a connector and a group");
            }
        }
    }

    private TeamMembership() {}

    /** Group names match exactly: {@code Admins} is not {@code admins}. */
    public static Set<TeamId> resolve(
            String connector, Set<String> groups, Collection<GroupMapping> mappings, Set<TeamId> explicitTeams) {
        Set<TeamId> teams = new HashSet<>(explicitTeams);
        for (GroupMapping mapping : mappings) {
            if (mapping.connector().equals(connector) && groups.contains(mapping.group())) {
                teams.add(mapping.team());
            }
        }
        return Set.copyOf(teams);
    }
}
