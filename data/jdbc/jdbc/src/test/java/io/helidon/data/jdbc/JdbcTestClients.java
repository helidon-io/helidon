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

import javax.sql.DataSource;

/**
 * Creates package scoped JDBC clients for tests that exercise provider
 * behavior with recording data sources.
 */
final class JdbcTestClients {

    private static final String DATA_SOURCE_NAME = "test-data-source";

    private JdbcTestClients() {
    }

    /**
     * Creates a client with the default parameter count cache configuration.
     *
     * @param dataSource recording data source
     * @return client that uses the recording data source
     */
    static JdbcClient create(DataSource dataSource) {
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSource(DATA_SOURCE_NAME)
                .buildPrototype();
        return create(dataSource, config);
    }

    /**
     * Creates a client with an explicit parameter count cache configuration.
     *
     * @param dataSource recording data source
     * @param capacity parameter count cache capacity
     * @param maxSqlLength maximum cacheable SQL length
     * @return client that uses the recording data source
     */
    static JdbcClient create(DataSource dataSource, int capacity, int maxSqlLength) {
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSource(DATA_SOURCE_NAME)
                .parameterCountCacheCapacity(capacity)
                .parameterCountCacheMaxSqlLength(maxSqlLength)
                .buildPrototype();
        return create(dataSource, config);
    }

    private static JdbcClient create(DataSource dataSource, JdbcClientConfig config) {
        return new JdbcClientImpl(config,
                                  dataSource,
                                  JdbcConnectionLease.ownedProvider(),
                                  JdbcClientConfigSupport.cachePolicy(config));
    }
}
