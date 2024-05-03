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

import java.nio.file.Path;
import java.util.Map;

import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.WaitStrategy;
import io.helidon.tests.integration.harness.TestProcess;
import io.helidon.tests.integration.harness.TestProcesses;
import io.helidon.tests.integration.jpa.common.RemoteTest;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for the remote tests.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestProcesses
abstract class OracleRemoteTest extends RemoteTest {

    @Container
    static final GenericContainer<?> CONTAINER = OracleTestContainer.CONTAINER;

    @TestProcess
    static final ProcessRunner PROCESS_RUNNER = ProcessRunner.of(ExecMode.CLASS_PATH)
            .finalName("helidon-tests-integration-jpa-oracle")
            .properties(Map.of("java.util.logging.config.file", Path.of("target/classes/logging.properties").toAbsolutePath()))
            .properties(OracleTestContainer::config)
            .waitingFor(WaitStrategy.waitForPort());

    /**
     * Create a new instance.
     *
     * @param path base path
     */
    @SuppressWarnings("resource")
    OracleRemoteTest(String path) {
        super(path, PROCESS_RUNNER.process().port());
    }
}
