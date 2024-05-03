/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.mysql;

import java.util.Map;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Database container utility.
 */
abstract class MySQLTestContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("container-registry.oracle.com/mysql/community-server")
            .asCompatibleSubstituteFor("mysql");

    static final MySQLContainer<?> CONTAINER = new MySQLContainer<>(IMAGE)
            .withPassword("mysql123");

    static Map<String, String> config() {
        return Map.of("javax.sql.DataSource.test.dataSource.url", CONTAINER.getJdbcUrl());
    }

    private MySQLTestContainer() {
    }
}
