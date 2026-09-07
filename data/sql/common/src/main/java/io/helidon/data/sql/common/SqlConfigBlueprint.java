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
 * SQL specific connection configuration.
 * <p>
 * Exactly one connection source is required: direct JDBC connection
 * properties, the name of a registered {@link DataSource}, or an existing
 * {@link DataSource} supplied programmatically.
 */
@Prototype.Blueprint(createFromConfigPublic = false, createEmptyPublic = false, decorator = SqlConfigSupport.Decorator.class)
@Prototype.Configured
@Prototype.CustomMethods(SqlConfigSupport.CustomMethods.class)
// Include the default accessor so generated builders expose dataSource(DataSource).
@Prototype.IncludeDefaultMethods("dataSource")
interface SqlConfigBlueprint {
    /**
     * Direct JDBC connection properties.
     *
     * @return configured connection properties
     */
    @Option.Configured
    Optional<ConnectionConfig> connection();

    /**
     * Name of the registered {@link DataSource} to use.
     *
     * @return configured data source name
     */
    // Preserve the established configuration key after making the Java option name type-specific.
    @Option.Configured("data-source")
    Optional<String> dataSourceName();

    /**
     * Existing {@link DataSource} owned by the application.
     * <p>
     * This option is available only through programmatic builders. The application
     * retains ownership of the data source lifecycle.
     *
     * @return configured data source
     */
    // Prevent generated diagnostic text from exposing pool implementation details or sensitive state.
    @Option.Confidential
    default Optional<DataSource> dataSource() {
        return Optional.empty();
    }

}
