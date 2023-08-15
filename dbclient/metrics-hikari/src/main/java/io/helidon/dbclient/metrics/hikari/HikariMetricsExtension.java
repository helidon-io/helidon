/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.metrics.hikari;

import java.util.function.Consumer;

import io.helidon.common.config.Config;
import io.helidon.dbclient.hikari.HikariMetricsRegistry;

import com.codahale.metrics.MetricRegistry;

/**
 * Registers JDBC connection pool metrics to {@code HikariConnectionPool}.
 */
final class HikariMetricsExtension implements HikariMetricsRegistry {

    private final Config config;
    private final boolean enabled;

    private HikariMetricsExtension(Config config, boolean enabled) {
        this.config = config;
        this.enabled = enabled;
    }

    /**
     * Create a new instance.
     *
     * @param config config
     * @return HikariCpExtension
     */
    static HikariMetricsRegistry create(Config config) {
        return new HikariMetricsExtension(config, config.get("enabled").asBoolean().orElse(true));
    }

    /**
     * Register {@link MetricRegistry} instance with listener into connection pool configuration.
     * Provided {@link MetricRegistry} instance consumer is responsible for setting this instance
     * to connection pool configuration.
     *
     * @param registry {@link MetricRegistry} instance consumer
     */
    @Override
    public void register(Consumer<Object> registry) {
        if (enabled) {
            MetricRegistry metricRegistry = new MetricRegistry();
            metricRegistry.addListener(DropwizardMetricsListener.create(config));
            registry.accept(metricRegistry);
        }
    }

}
