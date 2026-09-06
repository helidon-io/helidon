/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import javax.sql.DataSource;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * SQL specific configuration.
 * Database connection may be configured using connection string, username
 * and password, using a {@link DataSource} name, or by supplying an existing
 * {@link DataSource} programmatically.
 * Database connection must be configured by exactly one of the options
 * mentioned above.
 */
@Prototype.Blueprint(createFromConfigPublic = false, createEmptyPublic = false, decorator = SqlConfigSupport.Decorator.class)
@Prototype.Configured
@Prototype.CustomMethods(SqlConfigSupport.CustomMethods.class)
@Prototype.IncludeDefaultMethods("dataSource")
interface SqlConfigBlueprint {
    /**
     * Configuration of a direct connection to a database, with exactly one
     * connection source required.
     *
     * @return connection configuration
     */
    @Option.Configured
    Optional<ConnectionConfig> connection();

    /**
     * Name of the registered {@link DataSource}, with exactly one connection
     * source required.
     *
     * @return name used for data source lookup
     */
    // Preserve the established configuration key after making the Java option name type-specific.
    @Option.Configured("data-source")
    Optional<String> dataSourceName();

    /**
     * Existing {@link DataSource} owned by application, with exactly one
     * connection source required.
     * <p>
     * This option is available only to programmatic builders. The application
     * retains ownership of the data source lifecycle.
     *
     * @return configured existing data source
     */
    // Prevent generated diagnostic text from exposing pool implementation details or sensitive state.
    @Option.Confidential
    default Optional<DataSource> dataSource() {
        return Optional.empty();
    }

}
