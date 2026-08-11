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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.configurable.Resource;
import io.helidon.data.sql.common.SqlConfig;
import io.helidon.service.registry.Service;

/**
 * Configuration blueprint for one JDBC persistence unit.
 * <p>
 * The generated configuration type is read from {@code data.persistence-units.jdbc}. Exactly one inherited SQL
 * connection choice must be configured: a named datasource or a direct connection.
 */
@Api.Preview
@Prototype.Blueprint
@Prototype.Configured(JdbcPersistenceUnitFactory.CONFIG_KEY)
interface JdbcPersistenceUnitConfigBlueprint extends SqlConfig {

    /**
     * Persistence-unit name used as the service-registry qualifier.
     *
     * @return persistence-unit name
     */
    @Option.Configured
    @Option.Default(Service.Named.DEFAULT_NAME)
    String name();

    /**
     * Resource containing the database initialization script.
     * URI-backed resources are not supported because the common resource
     * opens a URI before the JDBC bootstrap policy can validate it.
     *
     * @return database initialization script resource
     */
    @Option.Configured
    Optional<Resource> initScript();

    /**
     * Resource containing the database drop script.
     * URI-backed resources are not supported because the common resource
     * opens a URI before the JDBC bootstrap policy can validate it.
     *
     * @return database drop script resource
     */
    @Option.Configured
    Optional<Resource> dropScript();

}
