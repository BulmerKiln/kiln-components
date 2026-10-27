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
import com.bulmerkiln.registry.store.NamespaceStore;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who may do what to a package, decided before anything about it is looked up (FMT-NPM-9). A
 * scoped package belongs to the namespace that owns its scope; an unscoped one to none, so it can
 * be read where policy allows but never published.
 */
public final class NpmAccessService {

    private final Database database;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public NpmAccessService(Database database, ArtifactAccess access, ArtifactPolicy policy) {
        this.database = database;
        this.access = access;
        this.policy = policy;
    }

    Optional<Namespace> require(NpmPackageRequest request, Operation operation) {
        String scope = request.name().scope();
        Optional<Namespace> namespace =
                scope == null ? Optional.empty() : database.transaction(tx -> NamespaceStore.forNpm(tx, "@" + scope));
        Optional<Refusal> refusal = access.check(artifact(request, namespace, null), operation)
                .toRefusal(
                        "You may not " + operation.name().toLowerCase(Locale.ROOT) + " " + request.name(),
                        () -> "Bearer realm=\"" + request.registryUrl() + "\"");
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
                        "No namespace owns " + request.name() + "; publish under a scope an admin has created"));
    }

    /** Whether policy refuses reading one version, or the whole package when it's null. */
    Optional<Refusal> readRefusal(NpmPackageRequest request, Optional<Namespace> namespace, @Nullable String version) {
        return policy.checkRead(artifact(request, namespace, version));
    }

    static ArtifactRef artifact(NpmPackageRequest request, Optional<Namespace> namespace, @Nullable String version) {
        String name = request.name().toString();
        return new ArtifactRef(
                NpmFormatModule.FORMAT, namespace.map(Namespace::name).orElse(name), name, version);
    }
}
