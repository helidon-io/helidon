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

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Future;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Creates PostgreSQL containers and configuration for JDBC chaos smoke tests.
 */
public final class ChaosPostgreSqlDatabase {
    /**
     * Test-only system property used by imperative chaos adapters to request generated key columns explicitly.
     */
    public static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.chaos.generated-key-column";

    /**
     * Named data source used by PostgreSQL one connection recovery tests.
     */
    public static final String HIKARI_SOURCE_NAME = "chaos-pgsql-hikari-source";

    private ChaosPostgreSqlDatabase() {
    }

    /**
     * Creates a PostgreSQL container from the local chaos integration test image.
     *
     * @return PostgreSQL container
     */
    public static JdbcDatabaseContainer<?> container() {
        ImageFromDockerfile image = new ImageFromDockerfile("data-jdbc-chaos-pgsql", false)
                .withFileFromPath(".", Path.of("../../common/src/pgsql/docker"));
        return new PostgreSQLContainer(image)
                .withPassword("pgsql123")
                .withInitScript("db/chaos/pgsql/schema-init.sql");
    }

    /**
     * Creates Helidon configuration from a started PostgreSQL container.
     *
     * @param container started container
     * @return PostgreSQL chaos configuration
     */
    public static Config config(JdbcDatabaseContainer<?> container) {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "@default",
                "data.clients.jdbc.0.connection.url", container.getJdbcUrl(),
                "data.clients.jdbc.0.connection.username", container.getUsername(),
                "data.clients.jdbc.0.connection.password", container.getPassword(),
                "data.clients.jdbc.0.connection.jdbc-driver-class-name", container.getDriverClassName())));
    }

    /**
     * Creates Helidon configuration for a named PostgreSQL Hikari data source.
     *
     * @return data source backed configuration
     */
    public static Config hikariConfig() {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "@default",
                "data.clients.jdbc.0.data-source", HIKARI_SOURCE_NAME)));
    }

    /**
     * Creates a bounded Hikari data source from a started PostgreSQL container.
     *
     * @param container started container
     * @return Hikari data source
     */
    public static HikariDataSource dataSource(JdbcDatabaseContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static final class PostgreSQLContainer extends JdbcDatabaseContainer<PostgreSQLContainer> {
        private String databaseName = "test";
        private String username = "test";
        private String password = "test";

        PostgreSQLContainer(Future<String> image) {
            super(image);
            waitStrategy = new LogMessageWaitStrategy()
                    .withRegEx(".*database system is ready to accept connections.*\\s")
                    .withTimes(2)
                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS));
            addExposedPort(5432);
        }

        @Override
        public PostgreSQLContainer withUsername(String username) {
            this.username = username;
            return this;
        }

        @Override
        public PostgreSQLContainer withPassword(String password) {
            this.password = password;
            return this;
        }

        @Override
        public PostgreSQLContainer withDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        @Override
        public String getDriverClassName() {
            return "org.postgresql.Driver";
        }

        @Override
        public String getJdbcUrl() {
            return "jdbc:postgresql://localhost:%d/%s".formatted(getMappedPort(5432), databaseName);
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        protected void configure() {
            withUrlParam("loggerLevel", "OFF");
            addEnv("POSTGRES_DB", databaseName);
            addEnv("POSTGRES_USER", username);
            addEnv("POSTGRES_PASSWORD", password);
        }

        @Override
        protected String getTestQueryString() {
            return "SELECT 1";
        }
    }
}
