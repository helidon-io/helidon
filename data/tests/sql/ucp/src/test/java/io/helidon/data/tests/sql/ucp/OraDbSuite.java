/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.sql.ucp;

import java.time.Duration;

import io.helidon.config.ConfigSources;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestContainerHandler;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class OraDbSuite implements SuiteProvider {

    private static final DockerImageName IMAGE = DockerImageName.parse(
            "container-registry.oracle.com/database/free");

    private final TestContainerHandler containerHandler;

    public OraDbSuite() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE);
        this.containerHandler = SqlTestContainerConfig.configureContainer(container,
                                                                          ConfigSources.classpath("application.yaml"));

        this.containerHandler.config()
                .get("test.database.password")
                .asString()
                .ifPresent(password -> container.withEnv("ORACLE_PWD", password));

        container.withExposedPorts(this.containerHandler.originalPort())
                .waitingFor(Wait.forHealthcheck()
                                    .withStartupTimeout(Duration.ofMinutes(5)));
    }

    @BeforeSuite
    public void beforeSuite() {
        this.containerHandler.startContainer();
        this.containerHandler.setConfig();
    }

    @AfterSuite
    public void afterSuite() {
        containerHandler.stopContainer();
    }
}
