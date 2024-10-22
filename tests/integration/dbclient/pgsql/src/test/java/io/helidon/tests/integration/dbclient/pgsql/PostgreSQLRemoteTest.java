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
import java.util.Map;

import io.helidon.tests.integration.dbclient.common.RemoteTest;
import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.WaitStrategy;
import io.helidon.tests.integration.harness.TestProcess;
import io.helidon.tests.integration.harness.TestProcesses;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for the remote tests.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestProcesses
abstract class PostgreSQLRemoteTest extends RemoteTest {

    @Container
    static final JdbcDatabaseContainer<?> CONTAINER = PostgreSQLTestContainer.CONTAINER;

    @TestProcess
    static final ProcessRunner PROCESS_RUNNER = ProcessRunner.of(ExecMode.CLASS_PATH)
            .finalName("helidon-tests-integration-dbclient-pgsql")
            .properties(Map.of("java.util.logging.config.file", Path.of("target/classes/logging.properties").toAbsolutePath()))
            .properties(PostgreSQLTestContainer::config)
            .waitingFor(WaitStrategy.waitForPort());

    /**
     * Create a new instance.
     *
     * @param path base path
     */
    @SuppressWarnings("resource")
    PostgreSQLRemoteTest(String path) {
        super(path, PROCESS_RUNNER.process().port());
    }
}
