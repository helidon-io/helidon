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
package io.helidon.tests.integration.jpa.oracle;

import java.time.Duration;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Database container utility.
 */
abstract class OracleTestContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("container-registry.oracle.com/database/express");

    static final GenericContainer<?> CONTAINER = new GenericContainer<>(IMAGE)
            .withEnv("ORACLE_PWD", "oracle123")
            .withExposedPorts(1521)
            .withStartupAttempts(5)
            .waitingFor(Wait.forHealthcheck()
                    .withStartupTimeout(Duration.ofMinutes(5)));

    static Map<String, String> config() {
        String jdbcUrl = String.format("jdbc:oracle:thin:@localhost:%s/XE", CONTAINER.getMappedPort(1521));
        return Map.of("javax.sql.DataSource.test.dataSource.url", jdbcUrl);
    }

    private OracleTestContainer() {
    }
}
