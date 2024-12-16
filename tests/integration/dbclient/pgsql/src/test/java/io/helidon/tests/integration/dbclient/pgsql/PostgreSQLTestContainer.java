/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.pgsql;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Database container utility.
 */
abstract class PostgreSQLTestContainer {

    private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile("pgsql", false)
            .withFileFromPath(".", Path.of("etc/docker"));

    static final JdbcDatabaseContainer<?> CONTAINER = new PostgreSQLContainer(IMAGE)
            .withPassword("pgsql123");

    static Map<String, Supplier<?>> config() {
        return Map.of("db.connection.url", CONTAINER::getJdbcUrl);
    }

    private PostgreSQLTestContainer() {
    }

    private static final class PostgreSQLContainer extends JdbcDatabaseContainer<PostgreSQLContainer> {

        private String dbName = "test";
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
        protected void configure() {
            // Disable Postgres driver use of java.util.logging to reduce noise at startup time
            withUrlParam("loggerLevel", "OFF");
            addEnv("POSTGRES_DB", dbName);
            addEnv("POSTGRES_USER", username);
            addEnv("POSTGRES_PASSWORD", password);
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
        public PostgreSQLContainer withDatabaseName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        @Override
        public String getDriverClassName() {
            return "org.postgresql.Driver";
        }

        @Override
        public String getJdbcUrl() {
            return "jdbc:postgresql://localhost:%d/%s".formatted(getMappedPort(5432), dbName);
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
        protected String getTestQueryString() {
            return "SELECT 1";
        }
    }
}
