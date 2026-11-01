/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.registry.lookup.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.mapper.NamespaceMapperImpl;
import com.bulmerkiln.registry.mapper.PackageMapperImpl;
import com.bulmerkiln.registry.policy.RegistryPolicy;
import com.bulmerkiln.registry.repository.BlobRepository;
import com.bulmerkiln.registry.repository.BlockRepository;
import com.bulmerkiln.registry.repository.NamespaceClaimRepository;
import com.bulmerkiln.registry.repository.NamespaceRepository;
import com.bulmerkiln.registry.repository.PackageFileRepository;
import com.bulmerkiln.registry.repository.PackageRepository;
import com.bulmerkiln.registry.repository.PackageVersionRepository;
import com.bulmerkiln.registry.repository.TombstoneRepository;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.registry.service.PackageService;
import lombok.Value;

/**
 * The registry's services and ports over a test database, built as the auto-configuration builds
 * them, for tests and format harnesses that wire services by hand.
 */
@Value
public final class KilnRegistry {

    private final NamespaceService namespaces;

    private final PackageService packages;

    private final BlobRecordService blobs;

    private final BlockService blocks;

    private final RegistryPolicy policy;

    private final NamespaceVisibilityLookup visibility;

    public static KilnRegistry of(Database database) {
        NamespaceService namespaces = new NamespaceService(
                KilnPostgres.repository(database, NamespaceRepository.class),
                KilnPostgres.repository(database, NamespaceClaimRepository.class),
                new NamespaceMapperImpl());
        PackageService packages = new PackageService(
                KilnPostgres.repository(database, PackageRepository.class),
                KilnPostgres.repository(database, PackageVersionRepository.class),
                KilnPostgres.repository(database, PackageFileRepository.class),
                new PackageMapperImpl());
        BlockService blocks = new BlockService(
                KilnPostgres.repository(database, BlockRepository.class),
                KilnPostgres.repository(database, TombstoneRepository.class));
        return new KilnRegistry(
                namespaces,
                packages,
                new BlobRecordService(KilnPostgres.repository(database, BlobRepository.class), new PackageMapperImpl()),
                blocks,
                new RegistryPolicy(database, blocks),
                new NamespaceVisibilityLookup(database, namespaces));
    }
}
