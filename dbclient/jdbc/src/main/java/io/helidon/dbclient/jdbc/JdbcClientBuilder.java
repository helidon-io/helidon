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
package io.helidon.dbclient.jdbc;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.common.CommonClientBuilder;
import io.helidon.dbclient.spi.DbClientBuilder;

/**
 * Fluent API builder for {@link JdbcClientBuilder} that implements
 * the {@link DbClientBuilder} from Helidon DB API.
 */
final class JdbcClientBuilder
        extends CommonClientBuilder<JdbcClientBuilder>
        implements DbClientBuilder<JdbcClientBuilder> {

    private JdbcConnectionPool connectionPool;

    JdbcClientBuilder() {
        super();
    }

    @Override
    public DbClient build() {
        return new JdbcClient(this);
    }

    @Override
    public JdbcClientBuilder config(Config config) {
        super.config(config);
        config.get("connection")
                .detach()
                .ifExists(cfg -> connectionPool(JdbcConnectionPool.create(cfg)));
        return this;
    }

    /**
     * Configure a connection pool.
     *
     * @param connectionPool connection pool to get connections to a database
     * @return updated builder instance
     */
    public JdbcClientBuilder connectionPool(JdbcConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        return this;
    }

    JdbcConnectionPool connectionPool() {
        return connectionPool;
    }

}
