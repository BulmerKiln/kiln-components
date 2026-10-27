/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.store.NamespaceStore;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who may do what to an artifact, decided before anything about it is looked up (FMT-MVN-9). An
 * artifact belongs to the namespace owning the longest prefix of its groupId. Maven sends
 * credentials only after a challenge, so anonymous callers get {@code 401} with {@code Basic}.
 */
public final class MavenAccessService {

    private final Database database;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public MavenAccessService(Database database, ArtifactAccess access, ArtifactPolicy policy) {
        this.database = database;
        this.access = access;
        this.policy = policy;
    }

    Optional<Namespace> require(MavenPath path, Operation operation) {
        Optional<Namespace> namespace = database.transaction(tx -> NamespaceStore.forMaven(tx, path.groupId()));
        Optional<Refusal> refusal = access.check(artifact(path, namespace, null), operation)
                .toRefusal(
                        "You may not " + operation.name().toLowerCase(Locale.ROOT) + " " + name(path),
                        () -> "Basic realm=\"Kiln\"");
        if (refusal.isPresent()) {
            throw new RefusedException(refusal.get());
        }
        return namespace;
    }

    public Namespace requireToWrite(MavenPath path) {
        return require(path, Operation.PUSH)
                .orElseThrow(() -> new RefusedException(Refusal.of(
                        Reason.DENIED, "No namespace owns group " + path.groupId() + "; an admin creates one")));
    }

    void checkRead(MavenPath path, Optional<Namespace> namespace, @Nullable String version) {
        readRefusal(path, namespace, version).ifPresent(refusal -> {
            throw new RefusedException(refusal);
        });
    }

    Optional<Refusal> readRefusal(MavenPath path, Optional<Namespace> namespace, @Nullable String version) {
        return policy.checkRead(artifact(path, namespace, version));
    }

    /** The artifact's name in the registry, {@code groupId:artifactId}. */
    static String name(MavenPath path) {
        return path.groupId() + ":" + path.artifactId();
    }

    static ArtifactRef artifact(MavenPath path, Optional<Namespace> namespace, @Nullable String version) {
        return new ArtifactRef(
                MavenFormatModule.FORMAT, namespace.map(Namespace::name).orElse(path.groupId()), name(path), version);
    }
}
