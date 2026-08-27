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
package io.helidon.data.jdbc.tests.database;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Future;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * PostgreSQL Testcontainers fixture shared by imperative and declarative JDBC tests.
 */
public final class PostgreSqlDatabase {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private PostgreSqlDatabase() {
    }

    /**
     * Creates a PostgreSQL container from the local integration test image.
     *
     * @return container
     */
    public static JdbcDatabaseContainer<?> container() {
        ImageFromDockerfile image = new ImageFromDockerfile("data-jdbc-pgsql", false)
                .withFileFromPath(".", Path.of("../../common/src/pgsql/docker"));
        return new PostgreSQLContainer(image)
                .withPassword("pgsql123")
                .withInitScript("db/pgsql/schema-init.sql");
    }

    /**
     * Creates Helidon config from a started PostgreSQL container.
     *
     * @param container started container
     * @return config
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
