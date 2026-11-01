/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Access;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.MavenFormatModule;
import com.bulmerkiln.format.maven.domain.MavenPath;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
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
    private final NamespaceService namespaces;
    private final BlockService blocks;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public MavenAccessService(
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

    Optional<Namespace> require(MavenPath path, Operation operation) {
        Optional<Namespace> namespace = owner(path.getGroupId());
        Optional<Refusal> refusal = access.check(artifact(path, namespace, null), operation)
                .toRefusal(
                        "You may not " + operation.name().toLowerCase(Locale.ROOT) + " " + name(path),
                        () -> "Basic realm=\"Kiln\"");
        if (refusal.isPresent()) {
            throw new RefusedException(refusal.get());
        }
        return namespace;
    }

    /** Whether the caller may read {@code path}, for choosing what to list; never throws. */
    boolean mayRead(MavenPath path) {
        Optional<Namespace> namespace = owner(path.getGroupId());
        return allowed(artifact(path, namespace, null), Operation.PULL)
                && readRefusal(path, namespace, null).isEmpty();
    }

    /**
     * Uploading metadata. A path without a version is both artifact {@code g:a} and group
     * {@code g.a}, whose plugin metadata Maven deploys there, so writing either will do (FMT-MVN-17).
     */
    public void requireToWriteMetadata(MavenPath.Metadata metadata) {
        if (metadata.getVersion() == null) {
            String group = metadata.getGroupId() + "." + metadata.getArtifactId();
            Optional<Namespace> owner = owner(group);
            if (owner.isPresent()
                    && allowed(
                            new ArtifactRef(
                                    MavenFormatModule.FORMAT, owner.get().getName(), group, null),
                            Operation.PUSH)) {
                return;
            }
        }
        requireToWrite(metadata);
    }

    /** The namespace owning a groupId: the longest claimed prefix, {@code org.acme} owning {@code org.acme.core}. */
    private Optional<Namespace> owner(String groupId) {
        return database.transaction(() -> namespaces.owner(MavenFormatModule.FORMAT, groupId));
    }

    /**
     * Refuses writing {@code file}'s version when a block or tombstone stops it, inside the caller's
     * transaction, so the check and the write commit together (REG-4).
     */
    void checkWrite(MavenPath.ArtifactFile file, Namespace namespace) {
        blocks.checkWrite(artifact(file, Optional.of(namespace), file.getVersion()))
                .ifPresent(refusal -> {
                    throw new RefusedException(refusal);
                });
    }

    private boolean allowed(ArtifactRef artifact, Operation operation) {
        return access.check(artifact, operation) == Access.ALLOW;
    }

    public Namespace requireToWrite(MavenPath path) {
        return require(path, Operation.PUSH)
                .orElseThrow(() -> new RefusedException(Refusal.of(
                        Reason.DENIED, "No namespace owns group " + path.getGroupId() + "; an admin creates one")));
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
        return path.getGroupId() + ":" + path.getArtifactId();
    }

    static ArtifactRef artifact(MavenPath path, Optional<Namespace> namespace, @Nullable String version) {
        return new ArtifactRef(
                MavenFormatModule.FORMAT,
                namespace.map(Namespace::getName).orElse(path.getGroupId()),
                name(path),
                version);
    }
}
