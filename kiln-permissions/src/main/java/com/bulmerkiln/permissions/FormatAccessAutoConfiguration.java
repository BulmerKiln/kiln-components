/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import com.bulmerkiln.format.ArtifactAccess;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Implements the format subsystem's access port, when that subsystem is present. */
@AutoConfiguration(after = PermissionsAutoConfiguration.class)
@ConditionalOnClass(ArtifactAccess.class)
public class FormatAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactAccess.class)
    ArtifactAccess artifactAccess(PermissionPolicy policy, VisibilityLookup visibility) {
        return new FormatAccess(policy, visibility);
    }
}
