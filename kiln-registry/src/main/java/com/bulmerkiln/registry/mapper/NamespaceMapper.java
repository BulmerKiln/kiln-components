/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.mapper;

import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.NamespaceEntity;
import java.util.Locale;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Namespaces from their rows; visibility is stored in lower case. */
@Mapper
public interface NamespaceMapper {

    @Mapping(target = "id", source = "id.id")
    Namespace toNamespace(NamespaceEntity entity);

    default Visibility toVisibility(String visibility) {
        return Visibility.valueOf(visibility.toUpperCase(Locale.ROOT));
    }

    default String toColumn(Visibility visibility) {
        return visibility.name().toLowerCase(Locale.ROOT);
    }
}
