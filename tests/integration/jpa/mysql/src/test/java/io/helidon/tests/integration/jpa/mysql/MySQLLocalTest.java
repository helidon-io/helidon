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

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.testing.AddConfigSource;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for the local tests.
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class MySQLLocalTest {

    @Container
    static final MySQLContainer<?> CONTAINER = MySQLTestContainer.CONTAINER;

    @AddConfigSource
    static ConfigSource config() {
        return MpConfigSources.create(MySQLTestContainer.config());
    }
}
