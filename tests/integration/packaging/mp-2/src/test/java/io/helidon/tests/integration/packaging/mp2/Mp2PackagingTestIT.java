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
package io.helidon.tests.integration.packaging.mp2;

import java.time.Duration;
import java.util.Map;

import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.ProcessMonitor;
import io.helidon.tests.integration.harness.WaitStrategy;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testcontainers(disabledWithoutDocker = true)
abstract class Mp2PackagingTestIT {

    private static final DockerImageName image = DockerImageName.parse("container-registry.oracle.com/database/express");

    @Container
    static final GenericContainer<?> CONTAINER = new GenericContainer<>(image)
            .withEnv("ORACLE_PWD", "oracle123")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("/init.sql"),
                    "/opt/oracle/scripts/startup/init.sql")
            .withExposedPorts(1521)
            .waitingFor(Wait.forHealthcheck()
                    .withStartupTimeout(Duration.ofMinutes(5)));

    abstract ExecMode execMode();

    void doTestApp() {
        try (ProcessMonitor process = process(Map.of(
                "javax.sql.DataSource.test.dataSource.url", jdbcUrl(),
                "javax.sql.DataSource.test.dataSource.password", "oracle123"))
                .await(WaitStrategy.waitForCompletion())) {

            assertThat(process.get().exitValue(), is(0));
        }
    }

    private ProcessMonitor process(Map<String, ?> properties) {
        return ProcessRunner.of(execMode())
                .finalName("helidon-tests-integration-packaging-mp-2")
                .properties(properties)
                .start();
    }

    private String jdbcUrl() {
        return String.format("jdbc:oracle:thin:@localhost:%d/XE", CONTAINER.getMappedPort(1521));
    }
}
