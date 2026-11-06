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
package io.helidon.data.sql.datasource;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

/**
 * Executes an independent SELECT for each data source health request.
 * Register an instance with a HealthObserver builder.
 *
 * <p>The caller retains ownership of the data source. Each connection must be independent of caller transactions
 * and already have auto-commit enabled. The probe never changes auto-commit, commits, or rolls back, including
 * during cleanup. An external wrapper that associates connections with transactions must supply independent
 * connections when a caller transaction is active.</p>
 *
 * <p>The data source must discard unsafe physical pool entries when their connections are aborted.
 * The query timeout does not bound acquisition, network operations, or HTTP requests.</p>
 */
public final class DataSourceHealthCheck implements HealthCheck, RuntimeType.Api<DataSourceHealthCheckConfig> {
    private final DataSourceHealthCheckConfig config;
    private final String query;
    private final int timeout;

    private DataSourceHealthCheck(DataSourceHealthCheckConfig config) {
        this.config = config;
        if (config.name().isPresent() && config.name().get().isBlank()) {
            throw new IllegalArgumentException("An explicit health check name must not be blank.");
        }
        this.query = DataSourceHealthQuery.validate(config.query());
        Duration queryTimeout = config.queryTimeout();
        long seconds = queryTimeout.getSeconds();
        if (queryTimeout.getNano() != 0 || seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "The query timeout must contain positive whole seconds within the JDBC integer range.");
        }
        this.timeout = (int) seconds;
    }

    /**
     * Creates a builder that defaults to readiness and a query timeout of two seconds.
     *
     * @return The builder requires an existing data source.
     */
    public static DataSourceHealthCheckConfig.Builder builder() {
        return DataSourceHealthCheckConfig.builder();
    }

    /**
     * Creates a health check without acquiring a connection.
     *
     * @param config The configuration defines the check over an existing data source.
     * @return The check acquires a connection only when invoked.
     * @throws NullPointerException The configuration is null.
     * @throws IllegalArgumentException The probe configuration is invalid or unsupported.
     */
    public static DataSourceHealthCheck create(DataSourceHealthCheckConfig config) {
        Objects.requireNonNull(config, "The health check configuration must not be null.");
        return new DataSourceHealthCheck(config);
    }

    /**
     * Creates a check with the supplied builder updates.
     *
     * @param consumer The consumer configures the builder.
     * @return The configured health check acquires no resources until called.
     * @throws NullPointerException The consumer is null.
     */
    public static DataSourceHealthCheck create(Consumer<DataSourceHealthCheckConfig.Builder> consumer) {
        Objects.requireNonNull(consumer, "The builder consumer must not be null.");
        return builder().update(consumer).build();
    }

    @Override
    public DataSourceHealthCheckConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name().orElseGet(HealthCheck.super::name);
    }

    @Override
    public HealthCheckType type() {
        return config.type();
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder().status(DataSourceHealthProbe.execute(config.dataSource(), query, timeout)).build();
    }
}
