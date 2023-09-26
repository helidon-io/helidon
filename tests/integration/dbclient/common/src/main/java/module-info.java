/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

/**
 * Helidon Database Client Integration Tests.
 */
module io.helidon.tests.integration.dbclient.common {

    requires org.junit.jupiter.api;
    requires java.net.http;
    requires jakarta.json;
    requires hamcrest.all;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.dbclient;
    requires io.helidon.dbclient.metrics;
    requires io.helidon.webserver;
    requires io.helidon.webserver.observe;
    requires io.helidon.dbclient.health;
    requires io.helidon.health;
    requires io.helidon.webserver.observe.health;

    exports io.helidon.tests.integration.dbclient.common.spi;
    exports io.helidon.tests.integration.dbclient.common.tests;
    exports io.helidon.tests.integration.dbclient.common.utils;
    exports io.helidon.tests.integration.dbclient.common.model;

    uses io.helidon.tests.integration.dbclient.common.spi.SetupProvider;

    provides io.helidon.dbclient.spi.DbMapperProvider
            with io.helidon.tests.integration.dbclient.common.utils.MapperProvider;
}