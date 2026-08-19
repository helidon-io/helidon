/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.jdbc.tests.chaos.support;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Creates the H2 configuration used by H2 chaos smoke tests.
 */
public final class ChaosH2Database {
    /**
     * Dedicated in-memory URL for isolated JDBC chaos smoke tests.
     */
    public static final String URL = "jdbc:h2:mem:"
            + ChaosSecrets.DATABASE_NAME_CANARY
            + ";DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;IGNORE_UNKNOWN_SETTINGS=TRUE;PRIVATE_CHAOS_URL_CANARY="
            + ChaosSecrets.URL_CANARY;

    /**
     * Named datasource used by Hikari one-connection recovery tests.
     */
    public static final String HIKARI_SOURCE_NAME = "chaos-hikari-source";

    private ChaosH2Database() {
    }

    /**
     * Creates Helidon configuration for the H2 chaos persistence unit.
     *
     * @return H2 chaos configuration
     */
    public static Config config() {
        return Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", URL,
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "org.h2.Driver",
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/chaos/h2/schema-init.sql")));
    }

    /**
     * Creates Helidon configuration for a named Hikari datasource.
     *
     * @return H2 Hikari chaos configuration
     */
    public static Config hikariConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", HIKARI_SOURCE_NAME,
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/chaos/h2/schema-init.sql")));
    }

    /**
     * Creates a bounded Hikari datasource for pool recovery smoke tests.
     *
     * @return Hikari datasource
     */
    public static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }
}
