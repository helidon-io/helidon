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

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.data.jdbc.JdbcHealthSupport;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

/**
 * Executes a fresh independent JDBC probe for each health request.
 * Register an imperative instance through the existing HealthObserver builder.
 */
public final class JdbcClientHealthCheck implements HealthCheck, RuntimeType.Api<JdbcClientHealthCheckConfig> {
    private final JdbcClientHealthCheckConfig config;
    private final BooleanSupplier probe;

    private JdbcClientHealthCheck(JdbcClientHealthCheckConfig config) {
        this.config = config;
        if (config.name().isPresent() && config.name().get().isBlank()) {
            throw new IllegalArgumentException("An explicit health check name must not be blank.");
        }
        this.probe = JdbcHealthSupport.createProbe(config.client(), config.query(), config.queryTimeout());
    }

    /**
     * Creates a builder with readiness membership and a two second query timeout.
     *
     * @return The new builder requires an existing client.
     */
    public static JdbcClientHealthCheckConfig.Builder builder() {
        return JdbcClientHealthCheckConfig.builder();
    }

    /**
     * Creates a health check without acquiring a connection.
     *
     * @param config The configuration describes a check over an existing client.
     * @return The check can be registered with the existing health observer.
     * @throws NullPointerException The configuration is null.
     * @throws IllegalArgumentException The probe configuration is invalid or unsupported.
     */
    public static JdbcClientHealthCheck create(JdbcClientHealthCheckConfig config) {
        Objects.requireNonNull(config, "The health check configuration must not be null.");
        return new JdbcClientHealthCheck(config);
    }

    /**
     * Creates a check after applying programmatic builder updates.
     *
     * @param consumer The consumer supplies builder updates.
     * @return The configured health check acquires no resources until called.
     * @throws NullPointerException The consumer is null.
     */
    public static JdbcClientHealthCheck create(Consumer<JdbcClientHealthCheckConfig.Builder> consumer) {
        Objects.requireNonNull(consumer, "The builder consumer must not be null.");
        return builder().update(consumer).build();
    }

    @Override
    public JdbcClientHealthCheckConfig prototype() {
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
        return HealthCheckResponse.builder().status(probe.getAsBoolean()).build();
    }
}
