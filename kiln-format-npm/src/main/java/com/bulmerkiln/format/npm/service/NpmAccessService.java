/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.NpmFormatModule;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who may do what to a package, decided before anything about it is looked up (FMT-NPM-9). A
 * scoped package belongs to the namespace that owns its scope; an unscoped one to the namespace
 * claiming the longest prefix of its name, or to none, so it can be read where policy allows but
 * not published.
 */
public final class NpmAccessService {

    private final Database database;
    private final NamespaceService namespaces;
    private final BlockService blocks;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public NpmAccessService(
            Database database,
            NamespaceService namespaces,
            BlockService blocks,
            ArtifactAccess access,
            ArtifactPolicy policy) {
        this.database = database;
        this.namespaces = namespaces;
        this.blocks = blocks;
        this.access = access;
        this.policy = policy;
    }

    /**
     * The scope is claimed as {@code @acme} with separator {@code /}, so it owns {@code @acme/ui};
     * an unscoped name prefix as {@code acme-} with none.
     */
    Optional<Namespace> require(NpmPackageRequest request, Operation operation) {
        Optional<Namespace> namespace = database.transaction(
                () -> namespaces.owner(NpmFormatModule.FORMAT, request.getName().toString()));
        Optional<Refusal> refusal = access.check(artifact(request, namespace, null), operation)
                .toRefusal(
                        "You may not " + operation.name().toLowerCase(Locale.ROOT) + " " + request.getName(),
                        () -> "Bearer realm=\"" + request.getRegistryUrl() + "\"");
        if (refusal.isPresent()) {
            throw new RefusedException(refusal.get());
        }
        return namespace;
    }

    /** As {@link #require}, for a write, which needs the namespace that owns the scope. */
    Namespace requireToWrite(NpmPackageRequest request, Operation operation) {
        return require(request, operation)
                .orElseThrow(() -> new NpmException(
                        Reason.DENIED,
                        "No namespace owns " + request.getName()
                                + "; publish under a scope or name prefix an admin has created"));
    }

    /**
     * Refuses writing {@code version} when a block or tombstone stops it, inside the caller's
     * transaction, so the check and the write commit together (REG-4).
     */
    void checkWrite(NpmPackageRequest request, Namespace namespace, String version) {
        blocks.checkWrite(artifact(request, Optional.of(namespace), version)).ifPresent(refusal -> {
            throw new RefusedException(refusal);
        });
    }

    /** Whether policy refuses reading one version, or the whole package when it's null. */
    Optional<Refusal> readRefusal(NpmPackageRequest request, Optional<Namespace> namespace, @Nullable String version) {
        return policy.checkRead(artifact(request, namespace, version));
    }

    static ArtifactRef artifact(NpmPackageRequest request, Optional<Namespace> namespace, @Nullable String version) {
        String name = request.getName().toString();
        return new ArtifactRef(
                NpmFormatModule.FORMAT, namespace.map(Namespace::getName).orElse(name), name, version);
    }
}
