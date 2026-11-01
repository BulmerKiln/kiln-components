/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.service;

import com.bulmerkiln.permissions.domain.Ids.TeamId;
import com.bulmerkiln.permissions.domain.TeamGroupMapping;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Turns the groups an IdP reported at sign-in into teams. Recomputed at every sign-in, so a group
 * removed at the IdP takes its teams with it. No groups, or none mapped, leaves only explicit
 * teams: least privilege.
 */
public final class TeamMembershipService {

    private TeamMembershipService() {}

    /** Group names match exactly: {@code Admins} is not {@code admins}. */
    public static Set<TeamId> resolve(
            String connector, Set<String> groups, Collection<TeamGroupMapping> mappings, Set<TeamId> explicitTeams) {
        Set<TeamId> teams = new HashSet<>(explicitTeams);
        for (TeamGroupMapping mapping : mappings) {
            if (mapping.getConnector().equals(connector) && groups.contains(mapping.getGroup())) {
                teams.add(mapping.getTeam());
            }
        }
        return Set.copyOf(teams);
    }
}
