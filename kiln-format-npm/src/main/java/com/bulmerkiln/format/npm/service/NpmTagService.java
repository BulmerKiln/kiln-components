/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.service;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.npm.NpmException;
import com.bulmerkiln.format.npm.domain.NpmVersion;
import com.bulmerkiln.format.npm.dto.NpmPackageRequest;
import com.bulmerkiln.format.npm.entity.NpmPackageEntity;
import com.bulmerkiln.format.npm.store.NpmPackageStore;
import com.bulmerkiln.registry.store.PackageStore;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * {@code npm dist-tag add} and {@code rm}. A tag can only point at a version that exists, and
 * {@code latest} can be moved but never removed (FMT-NPM-14).
 */
public final class NpmTagService {

    private final Database database;
    private final NpmAccessService access;

    public NpmTagService(Database database, NpmAccessService access) {
        this.database = database;
        this.access = access;
    }

    public void set(NpmPackageRequest request, String tag, NpmVersion version) {
        access.requireToWrite(request, Operation.PUSH);
        change(
                request,
                tags -> {
                    tags.put(tag, version.value());
                    return true;
                },
                version.value());
    }

    public void remove(NpmPackageRequest request, String tag) {
        access.requireToWrite(request, Operation.PUSH);
        if ("latest".equals(tag)) {
            throw new NpmException(Reason.INVALID, "latest can be moved but not removed");
        }
        change(request, tags -> tags.remove(tag) != null, null);
    }

    private void change(
            NpmPackageRequest request, Predicate<Map<String, String>> edit, @Nullable String requiredVersion) {
        database.run(tx -> {
            NpmPackageEntity snapshot = NpmPackageStore.load(tx, request.name()).orElseThrow(NpmException::notFound);
            if (requiredVersion != null) {
                snapshot.version(requiredVersion);
            }
            Map<String, String> tags = snapshot.distTags();
            if (!edit.test(tags)) {
                throw NpmException.notFound();
            }
            PackageStore.updateMetadata(tx, snapshot.entry().id(), NpmPackageStore.metadata(tags));
        });
    }
}
