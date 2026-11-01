/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.autoconfigure;

import com.bulmerkiln.auth.session.service.SessionService;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Web sessions in the database, kept as the SHA-256 of their IDs. */
@AutoConfiguration(after = DatabaseAutoConfiguration.class)
@ConditionalOnClass(Database.class)
public class SessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SessionService sessionService(Database database) {
        return new SessionService(database, Clock.systemUTC());
    }
}
