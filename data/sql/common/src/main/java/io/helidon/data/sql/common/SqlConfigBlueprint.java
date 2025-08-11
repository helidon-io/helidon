/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.sql.common;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * SQL specific configuration.
 * Database connection may be configured using connection string, username
 * and password or using {@link javax.sql.DataSource} name.
 * Database connection must be configured by exactly one of the options
 * mentioned above.
 */
@Prototype.Blueprint(createFromConfigPublic = false, createEmptyPublic = false, decorator = SqlConfigSupport.Decorator.class)
@Prototype.Configured
interface SqlConfigBlueprint {
    /**
     * Configuration of a connection to a database.
     * Alternative is to use {@link SqlConfig.Builder#dataSource()}.
     *
     * @return connection configuration
     */
    @Option.Configured
    Optional<ConnectionConfig> connection();

    /**
     * Name of the {@link javax.sql.DataSource}.
     *
     * @return the name to use for {@link javax.sql.DataSource} lookup
     */
    @Option.Configured
    Optional<String> dataSource();

}
