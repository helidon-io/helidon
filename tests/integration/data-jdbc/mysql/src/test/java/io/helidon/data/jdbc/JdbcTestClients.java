/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.util.Objects;

import javax.sql.DataSource;

/**
 * Creates package scoped clients for provider tests that require a pool or a
 * data source wrapper with observable resource behavior.
 */
final class JdbcTestClients {

    private static final String DATA_SOURCE_NAME = "test-data-source";

    private JdbcTestClients() {
    }

    /**
     * Creates a standalone client that owns every connection lease.
     *
     * @param dataSource data source exercised by the test
     * @return standalone test client
     */
    static JdbcClient create(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "The test data source must not be null.");
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSource(DATA_SOURCE_NAME)
                .buildPrototype();
        return new JdbcClientImpl(config,
                                  dataSource,
                                  JdbcConnectionLease.ownedProvider(),
                                  JdbcClientConfigSupport.cachePolicy(config));
    }
}
