/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.service;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.NamespaceClaim;
import com.bulmerkiln.registry.mapper.NamespaceMapper;
import com.bulmerkiln.registry.repository.NamespaceClaimRepository;
import com.bulmerkiln.registry.repository.NamespaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Namespaces and the names they claim. Each lookup takes the longest matching claim, and a name no
 * namespace claims belongs to none (REG-1).
 */
public final class NamespaceService {

    private final NamespaceRepository namespaces;
    private final NamespaceClaimRepository claims;
    private final NamespaceMapper mapper;

    public NamespaceService(NamespaceRepository namespaces, NamespaceClaimRepository claims, NamespaceMapper mapper) {
        this.namespaces = namespaces;
        this.claims = claims;
        this.mapper = mapper;
    }

    /** Creates a namespace with its claims; a value another namespace claims in that format is refused. */
    public void create(Namespace namespace, List<NamespaceClaim> claimed) {
        UUID organization = organization();
        namespaces.add(
                organization, namespace.getId(), namespace.getName(), mapper.toColumn(namespace.getVisibility()));
        for (NamespaceClaim claim : claimed) {
            claims.add(
                    organization, namespace.getId(), claim.getFormat().getId(), claim.getValue(), claim.getSeparator());
        }
    }

    public Optional<Namespace> byName(String name) {
        return namespaces.findByName(organization(), name).map(mapper::toNamespace);
    }

    public Optional<Namespace> byId(UUID id) {
        return namespaces.findOne(organization(), id).map(mapper::toNamespace);
    }

    /** The namespace owning {@code name} in {@code format}: the one with the longest claim matching it. */
    public Optional<Namespace> owner(ArtifactFormat format, String name) {
        return namespaces.findOwner(organization(), format.getId(), name).map(mapper::toNamespace);
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
