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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL Testcontainers fixture shared by imperative and declarative JDBC tests.
 */
public final class MySqlDatabase {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private static final DockerImageName IMAGE = DockerImageName.parse(
                    "container-registry.oracle.com/mysql/community-server:9.7.1")
            .asCompatibleSubstituteFor("mysql");

    private MySqlDatabase() {
    }

    /**
     * Creates a MySQL container using the approved image.
     *
     * @return container
     */
    public static MySQLContainer<?> container() {
        return new MySQLContainer<>(IMAGE)
                .withUsername("test")
                .withPassword("mysql123")
                .withDatabaseName("test")
                .withUrlParam("preserveInstants", "true")
                .withUrlParam("connectionTimeZone", "UTC")
                .withUrlParam("forceConnectionTimeZoneToSession", "true")
                .withInitScript("db/mysql/schema-init.sql");
    }

    /**
     * Creates Helidon config from a started MySQL container.
     *
     * @param container started container
     * @return config
     */
    public static Config config(MySQLContainer<?> container) {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "@default",
                "data.clients.jdbc.0.connection.url", container.getJdbcUrl(),
                "data.clients.jdbc.0.connection.username", container.getUsername(),
                "data.clients.jdbc.0.connection.password", container.getPassword(),
                "data.clients.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver")));
    }
}
