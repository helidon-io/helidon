/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.sql.datasource.jdbc;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.data.sql.datasource.TransactionIsolation;
import io.helidon.data.sql.datasource.spi.DataSourceConfigProvider;

import static io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfigProvider.PROVIDER_TYPE;

/**
 * JDBC Data source configuration.
 */
@Prototype.Blueprint
@Prototype.Configured(root = false, value = PROVIDER_TYPE)
@Prototype.Provides(DataSourceConfigProvider.class)
interface JdbcDataSourceConfigBlueprint extends ConnectionConfig, ProviderConfig {

    /**
     * Type of this provider.
     *
     * @return the provider type
     */
    @Override
    default String type() {
        return PROVIDER_TYPE;
    }

    /**
     * Name of this provider.
     *
     * @return the provider name
     */
    @Override
    @Option.Default(PROVIDER_TYPE)
    String name();

    /**
     * Set the default auto-commit behavior of create connections.
     *
     * @return the desired auto-commit default for connections
     */
    @Option.Configured
    Optional<Boolean> autoCommit();

    /**
     * Set the default catalog name to be set on connections.
     *
     * @return the default catalog name
     */
    @Option.Configured
    Optional<String> catalog();

    /**
     * Whether the connection should be read only.
     *
     * @return if read only
     */
    @Option.Configured
    Optional<Boolean> readOnly();

    /**
     * Set the default schema name to be set on connections.
     *
     * @return the name of the default schema
     */
    @Option.Configured
    Optional<String> schema();

    /**
     * Set the default transaction isolation level.
     *
     * @return the isolation level
     */
    @Option.Configured
    Optional<TransactionIsolation> transactionIsolation();

    /**
     * Add properties (name/value pair) that will be used to configure the DataSource/Driver.
     * Property values are limited to {@link String} values.
     *
     * @return the properties
     */
    @Option.Configured
    @Option.Singular("property")
    Map<String, String> properties();
}
