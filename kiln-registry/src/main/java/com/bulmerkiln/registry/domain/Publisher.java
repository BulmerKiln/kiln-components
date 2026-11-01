/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.permissions.domain.Principal;
import java.util.Optional;
import java.util.UUID;
import lombok.Value;

/** Who published a version, as role bindings name subjects: a member or a service account. */
@Value
public final class Publisher {

    private final String kind;

    private final UUID id;

    /**
     * The publisher a principal is recorded as. A workload is recorded with trusted publishing,
     * which isn't built yet; anonymous callers never publish.
     */
    public static Optional<Publisher> of(Principal principal) {
        return switch (principal) {
            case Principal.Member member ->
                Optional.of(new Publisher("member", member.getId().getValue()));
            case Principal.ServiceAccount account ->
                Optional.of(new Publisher("service_account", account.getId().getValue()));
            case Principal.Workload _ -> Optional.empty();
            case Principal.Anonymous _ -> Optional.empty();
        };
    }
}
