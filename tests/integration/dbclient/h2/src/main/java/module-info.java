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
 * Helidon Database Client Integration Tests with H2 Database.
 */
module io.helidon.tests.integration.dbclient.h2 {

    requires java.sql;
    requires com.h2database;

    requires io.helidon.config;
    requires io.helidon.dbclient;
    requires io.helidon.tests.integration.dbclient.common;

    provides io.helidon.tests.integration.dbclient.common.spi.SetupProvider
            with io.helidon.tests.integration.dbclient.jdbc.H2SetupProvider;

}