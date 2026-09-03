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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.data.sql.common.SqlConfig;
import io.helidon.service.registry.Service;

/**
 * Configuration for a JDBC client.
 * <p>
 * A client can use a named data source or direct connection settings.
 * Registry managed clients are configured under {@code data.clients.jdbc}.
 */
@Api.Preview
@Prototype.Blueprint(createEmptyPublic = false, decorator = JdbcClientConfigSupport.Decorator.class)
@Prototype.Configured(JdbcClientConfigFactory.CONFIG_KEY)
interface JdbcClientConfigBlueprint extends SqlConfig, Prototype.Factory<JdbcClient> {

    /**
     * Logical name of this client.
     *
     * @return client name
     */
    @Option.Configured
    @Option.Default(Service.Named.DEFAULT_NAME)
    String name();

    /**
     * Maximum number of SQL marker counts retained by this client.
     * A value of zero disables retention while preserving marker validation.
     * This value is owned by Helidon Data JDBC and is not passed to JDBC.
     *
     * @return parameter count cache capacity
     */
    @Option.Configured("properties.jdbc.parameter-count-cache.capacity")
    @Option.DefaultInt(256)
    int parameterCountCacheCapacity();

    /**
     * Maximum SQL string length admitted to the parameter count cache in
     * UTF-16 code units. Longer SQL remains supported and is scanned without
     * being retained. This value is owned by Helidon Data JDBC and is not
     * passed to JDBC.
     *
     * @return maximum cacheable SQL length
     */
    @Option.Configured("properties.jdbc.parameter-count-cache.max-sql-length")
    @Option.DefaultInt(4096)
    int parameterCountCacheMaxSqlLength();
}
