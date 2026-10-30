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
package io.helidon.data.jdbc.health;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.health.HealthCheckType;

/**
 * Configures a health check over an existing client without duplicating connection settings.
 */
@Prototype.Blueprint
@Prototype.Configured
interface JdbcClientHealthCheckConfigBlueprint extends Prototype.Factory<JdbcClientHealthCheck> {
    /**
     * Supplies the required existing client programmatically.
     *
     * @return The client supplies the resolved datasource.
     */
    JdbcClient client();

    /**
     * Supplies trusted SELECT text without bind markers.
     *
     * @return The query defaults to SELECT 1.
     */
    @Option.Configured
    @Option.Confidential
    @Option.Default("SELECT 1")
    String query();

    /**
     * Sets positive whole seconds within the JDBC integer range.
     * This timeout does not bound connection acquisition or HTTP requests.
     *
     * @return The query timeout defaults to two seconds.
     */
    @Option.Configured
    @Option.Default("PT2S")
    Duration queryTimeout();

    /**
     * Selects membership in existing health endpoints.
     *
     * @return The membership defaults to readiness.
     */
    @Option.Configured
    @Option.Default("READINESS")
    HealthCheckType type();

    /**
     * Supplies a nonblank explicit name when individual addressability is required.
     *
     * @return Omission preserves the class based HealthCheck default name.
     */
    @Option.Configured
    Optional<String> name();
}
