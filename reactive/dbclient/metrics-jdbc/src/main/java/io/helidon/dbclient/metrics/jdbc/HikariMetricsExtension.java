/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.dbclient.metrics.jdbc;

import io.helidon.config.Config;
import io.helidon.dbclient.jdbc.HikariCpExtension;

import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariConfig;

/**
 * JDBC Configuration Interceptor for Metrics.
 *
 * Registers JDBC connection pool metrics to {@code HikariConnectionPool}.
 */
final class HikariMetricsExtension implements HikariCpExtension {
    private final Config config;
    private final boolean enabled;

    private HikariMetricsExtension(Config config, boolean enabled) {
        this.config = config;
        this.enabled = enabled;
    }

    static HikariMetricsExtension create(Config config) {
        return new HikariMetricsExtension(config, config.get("enabled").asBoolean().orElse(true));
    }

    /**
     * Register {@code MetricRegistry} instance with listener into Hikari CP configuration.
     *
     * @param poolConfig Hikari CP configuration
     */
    @Override
    public void configure(HikariConfig poolConfig) {
        if (enabled) {
            final MetricRegistry metricRegistry = new MetricRegistry();
            metricRegistry.addListener(DropwizardMetricsListener.create(config));
            poolConfig.setMetricRegistry(metricRegistry);
        }
    }
}
