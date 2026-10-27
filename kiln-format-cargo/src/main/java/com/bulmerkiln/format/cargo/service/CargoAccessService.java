/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.cargo.CargoException;
import com.bulmerkiln.format.cargo.CargoFormatModule;
import com.bulmerkiln.format.cargo.domain.CargoCrateName;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.store.NamespaceStore;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who may do what to a crate, decided before anything about it is looked up (FMT-CARGO-8). A crate
 * belongs to the namespace owning the longest prefix of its canonical name.
 */
public final class CargoAccessService {

    private final Database database;
    private final ArtifactAccess access;
    private final ArtifactPolicy policy;

    public CargoAccessService(Database database, ArtifactAccess access, ArtifactPolicy policy) {
        this.database = database;
        this.access = access;
        this.policy = policy;
    }

    Optional<Namespace> require(CargoCrateName name, Operation operation) {
        Optional<Namespace> namespace = database.transaction(tx -> NamespaceStore.forCargo(tx, name.canonical()));
        Optional<Refusal> refusal = access.check(artifact(name, namespace, null), operation)
                .toRefusal(
                        "You may not " + operation.name().toLowerCase(Locale.ROOT) + " " + name,
                        () -> "Cargo realm=\"Kiln\"");
        if (refusal.isPresent()) {
            throw new RefusedException(refusal.get());
        }
        return namespace;
    }

    Namespace requireToWrite(CargoCrateName name, Operation operation) {
        return require(name, operation)
                .orElseThrow(() -> new CargoException(
                        Reason.DENIED, "No namespace owns " + name + "; an admin creates one with a crate prefix"));
    }

    Optional<Refusal> readRefusal(CargoCrateName name, Optional<Namespace> namespace, @Nullable String version) {
        return policy.checkRead(artifact(name, namespace, version));
    }

    static ArtifactRef artifact(CargoCrateName name, Optional<Namespace> namespace, @Nullable String version) {
        return new ArtifactRef(
                CargoFormatModule.FORMAT,
                namespace.map(Namespace::name).orElse(name.canonical()),
                name.canonical(),
                version);
    }
}
