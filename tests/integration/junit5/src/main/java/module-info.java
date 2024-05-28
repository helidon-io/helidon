/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.helidon.tests.integration.junit5.DefaultDbClientProvider;

/**
 * Helidon Integration Tests jUnit5 Extension.
 */
module io.helidon.tests.integration.junit5 {

    requires org.junit.jupiter.api;
    requires testcontainers;

    requires io.helidon.builder.api;
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.config.metadata;
    requires io.helidon.dbclient;

    exports io.helidon.tests.integration.junit5;
    exports io.helidon.tests.integration.junit5.spi;

    uses io.helidon.tests.integration.junit5.spi.SuiteProvider;
    uses io.helidon.tests.integration.junit5.spi.ConfigProvider;
    uses io.helidon.tests.integration.junit5.spi.ContainerProvider;

    provides io.helidon.tests.integration.junit5.spi.ConfigProvider
            with io.helidon.tests.integration.junit5.DefaultConfigProvider;
    provides io.helidon.tests.integration.junit5.spi.ContainerProvider
            with io.helidon.tests.integration.junit5.MySqlContainer,
                    io.helidon.tests.integration.junit5.PgSqlContainer;
    provides io.helidon.tests.integration.junit5.spi.DbClientProvider
            with DefaultDbClientProvider;

}
