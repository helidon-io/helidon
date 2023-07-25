/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientBuilderBase;
import io.helidon.dbclient.spi.DbClientBuilder;

/**
 * Fluent API builder for {@link JdbcClientBuilder} that implements
 * the {@link DbClientBuilder} from Helidon DB API.
 */
public final class JdbcClientBuilder
        extends DbClientBuilderBase<JdbcClientBuilder>
        implements DbClientBuilder<JdbcClientBuilder> {

    private JdbcConnectionPool connectionPool;
    private JdbcParametersConfigBlueprint parametersConfig;

    JdbcClientBuilder() {
        super();
        this.parametersConfig = JdbcParametersConfig.create();
    }

    /**
     * Create a new instance.
     *
     * @return new JDBC client builder
     */
    public static JdbcClientBuilder create() {
        return new JdbcClientBuilder();
    }

    @Override
    public DbClient doBuild() {
        return new JdbcClient(this);
    }

    @Override
    public JdbcClientBuilder config(Config config) {
        super.config(config);
        config.get("connection").detach().map(JdbcConnectionPool::create).ifPresent(this::connectionPool);
        Config parameters = config.get("parameters");
        if (parameters.exists()) {
            this.parametersConfig = JdbcParametersConfig.create(parameters);
        }
        return this;
    }

    /**
     * Configure parameters setter.
     *
     * @param parametersConfig parameters setter configuration
     * @return updated builder instance
     */
    public JdbcClientBuilder parametersSetter(JdbcParametersConfig parametersConfig) {
        this.parametersConfig = parametersConfig;
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

    /**
     * Get the connection pool.
     *
     * @return connection pool
     */
    JdbcConnectionPool connectionPool() {
        return connectionPool;
    }

    /**
     * Get the parameters setter configuration.
     *
     * @return parameters setter configuration
     */
    JdbcParametersConfigBlueprint parametersConfig() {
        return parametersConfig;
    }

}
