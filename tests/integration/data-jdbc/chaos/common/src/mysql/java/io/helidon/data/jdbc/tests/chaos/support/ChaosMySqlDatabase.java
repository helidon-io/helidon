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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Creates MySQL containers and configuration for JDBC chaos smoke tests.
 */
public final class ChaosMySqlDatabase {
    /**
     * Test-only system property used by imperative chaos adapters to request generated key columns explicitly.
     */
    public static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.chaos.generated-key-column";

    /**
     * Named datasource used by MySQL one-connection recovery tests.
     */
    public static final String HIKARI_SOURCE_NAME = "chaos-mysql-hikari-source";

    private static final DockerImageName IMAGE = DockerImageName.parse(
                    "container-registry.oracle.com/mysql/community-server:9.7.1")
            .asCompatibleSubstituteFor("mysql");

    private ChaosMySqlDatabase() {
    }

    /**
     * Creates a MySQL container using the approved image.
     *
     * @return MySQL container
     */
    public static MySQLContainer<?> container() {
        return new MySQLContainer<>(IMAGE)
                .withUsername("test")
                .withPassword("mysql123")
                .withDatabaseName("test");
    }

    /**
     * Creates Helidon configuration from a started MySQL container.
     *
     * @param container started container
     * @return MySQL chaos configuration
     */
    public static Config config(MySQLContainer<?> container) {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", container.getJdbcUrl(),
                "data.persistence-units.jdbc.0.connection.username", container.getUsername(),
                "data.persistence-units.jdbc.0.connection.password", container.getPassword(),
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/chaos/mysql/schema-init.sql")));
    }

    /**
     * Creates Helidon configuration for a named MySQL Hikari datasource.
     *
     * @return datasource-backed configuration
     */
    public static Config hikariConfig() {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", HIKARI_SOURCE_NAME,
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/chaos/mysql/schema-init.sql")));
    }

    /**
     * Creates a bounded Hikari datasource from a started MySQL container.
     *
     * @param container started container
     * @return Hikari datasource
     */
    public static HikariDataSource dataSource(MySQLContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }
}
