/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.mapper;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.entity.BlobEntity;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Packages, versions, files and blob checksums from their rows; digests are stored as text. */
@Mapper
public interface PackageMapper {

    @Mapping(target = "id", source = "id.id")
    RegistryPackage toPackage(PackageEntity entity);

    @Mapping(target = "id", source = "id.id")
    PackageVersion toVersion(PackageVersionEntity entity);

    @Mapping(target = "id", source = "id.id")
    PackageFile toFile(PackageFileEntity entity);

    BlobChecksums toChecksums(BlobEntity entity);

    default Digest toDigest(String digest) {
        return Digest.parse(digest);
    }
}
