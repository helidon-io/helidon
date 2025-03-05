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
 */
@Prototype.Blueprint(createFromConfigPublic = false, createEmptyPublic = false)
@Prototype.Configured
interface SqlConfigBlueprint {
    /**
     * Database connection string.
     *
     * @return the connection string
     */
    @Option.Configured
    Optional<String> connectionString();

    /**
     * Username for the database connection.
     *
     * @return the username
     */
    @Option.Configured
    Optional<String> username();

    /**
     * Password for the database connection.
     *
     * @return the password
     */
    @Option.Configured
    @Option.Confidential
    Optional<char[]> password();

    /**
     * JDBC driver class for database connection.
     *
     * @return the JDBC driver class name
     */
    @Option.Configured
    Optional<String> jdbcDriverClassName();
}
