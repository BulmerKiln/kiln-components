/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Access;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.oci.OciException;
import com.bulmerkiln.format.oci.OciFormatModule;
import com.bulmerkiln.format.oci.constant.OciErrorCode;
import com.bulmerkiln.format.oci.dto.OciRepositoryRequest;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.store.NamespaceStore;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who may do what to a repository, and whether policy lets it be read or written (FMT-OCI-15).
 * Access is decided before anything about the repository is looked at, so the answer reveals
 * nothing about whether it exists.
 */
public final class OciAccessService {

    private final Database database;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public OciAccessService(Database database, ArtifactAccess access, ArtifactPolicy policy) {
        this.database = database;
        this.access = access;
        this.policy = policy;
    }

    /**
     * Refuses unless the caller may do {@code operation} on the repository, and returns the
     * namespace that owns it, if one does.
     */
    Optional<Namespace> require(OciRepositoryRequest request, Operation operation) {
        Optional<Namespace> namespace = database.transaction(
                tx -> NamespaceStore.forOci(tx, request.getRepository().getValue()));
        Access decision = access.check(artifact(request, namespace, null), operation);
        Optional<Refusal> refusal = decision.toRefusal(
                verb(operation) + " access to " + request.getAddressed() + " is denied",
                () -> challenge(request, operation));
        if (refusal.isPresent()) {
            throw new RefusedException(refusal.get());
        }
        return namespace;
    }

    /** As {@link #require}, for a write, which needs a namespace to create the repository in. */
    Namespace requireToWrite(OciRepositoryRequest request, Operation operation) {
        return require(request, operation)
                .orElseThrow(() -> new OciException(
                        OciErrorCode.DENIED, "No namespace owns " + request.getAddressed() + "; an admin creates one"));
    }

    /** Refuses reading {@code version} (a tag or digest) when policy blocks it. */
    void checkRead(OciRepositoryRequest request, Optional<Namespace> namespace, String version) {
        policy.checkRead(artifact(request, namespace, version)).ifPresent(refusal -> {
            throw new RefusedException(refusal);
        });
    }

    /** The artifact a policy or event is about. */
    static ArtifactRef artifact(OciRepositoryRequest request, Optional<Namespace> namespace, @Nullable String version) {
        String name = request.getRepository().getValue();
        // A name no namespace owns is still checked, under its first component, which owns nothing.
        String owner = namespace.map(Namespace::getName).orElse(name.split("/", 2)[0]);
        return new ArtifactRef(OciFormatModule.FORMAT, owner, name, version);
    }

    /** The token challenge docker follows: a Bearer realm and the scope it needs (FMT-OCI-16). */
    static String challenge(OciRepositoryRequest request, Operation operation) {
        String host = URI.create(request.getBaseUrl()).getHost();
        return "Bearer realm=\"" + request.getBaseUrl() + "/token\",service=\"" + host + "\",scope=\"repository:"
                + request.getAddressed() + ":" + scope(operation) + "\"";
    }

    private static String scope(Operation operation) {
        return switch (operation) {
            case PULL -> "pull";
            case PUSH -> "pull,push";
            case DELETE -> "delete";
        };
    }

    private static String verb(Operation operation) {
        String name = operation.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
