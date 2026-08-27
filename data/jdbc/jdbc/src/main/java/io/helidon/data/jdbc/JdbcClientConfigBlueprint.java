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

import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.service.registry.Service;

/**
 * Configuration for a JDBC client.
 * <p>
 * A client can use an existing {@link javax.sql.DataSource}, a named data
 * source, or direct connection settings. A data source object is available
 * only through the programmatic builder.
 * Registry managed clients are configured under {@code data.clients.jdbc}.
 */
@Api.Preview
@Prototype.Blueprint(createEmptyPublic = false, decorator = JdbcClientConfigSupport.Decorator.class)
@Prototype.Configured(JdbcClientConfigFactory.CONFIG_KEY)
@Prototype.CustomMethods(JdbcClientConfigSupport.CustomMethods.class)
@Prototype.Implement("io.helidon.data.sql.common.SqlConfig")
interface JdbcClientConfigBlueprint extends Prototype.Factory<JdbcClient> {

    /**
     * Logical name of this client.
     *
     * @return client name
     */
    @Option.Configured
    @Option.Default(Service.Named.DEFAULT_NAME)
    String name();

    /**
     * Configuration for a direct database connection.
     *
     * @return direct connection configuration or empty when none was supplied
     */
    @Option.Configured
    Optional<ConnectionConfig> connection();

    /**
     * Name used to look up a data source.
     *
     * @return data source name or empty when none was supplied
     */
    @Option.Configured
    Optional<String> dataSource();

    /**
     * Existing data source supplied by application code.
     *
     * @return existing data source or empty when none was supplied
     */
    @Option.Access("")
    @Option.Confidential
    Optional<DataSource> dataSourceInstance();

    /**
     * Options that configure Helidon Data JDBC behavior.
     * These values are not passed to a data source, driver, connection,
     * statement, or result set.
     *
     * @return JDBC client options
     */
    @Option.Configured
    @Option.DefaultMethod("create")
    JdbcPropertiesConfig properties();
}
