/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Properties;

import io.helidon.dbclient.common.DbConfig;
import io.helidon.dbclient.jdbc.JdbcClientExtension;

import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariConfig;

/**
 * JDBC Configuration Interceptor for Metrics.
 *
 * Registers JDBC connection pool metrics to {@code HikariConnectionPool}.
 */
public class JdbcMetricsExtension implements JdbcClientExtension {

    /**
     * Register {@code MetricRegistry} instance with listener into Hikari CP configuration.
     *
     * @param poolConfig Hikari CP configuration
     * @param properties internal configuration properties
     */
    @Override
    public void configure(final HikariConfig poolConfig, final Properties properties) {
        if (properties.containsKey(DbConfig.Properties.METRICS)) {
            String metrics = properties.getProperty(DbConfig.Properties.METRICS);
            if (metrics != null && DbConfig.Values.METRICS.equals(metrics.toLowerCase())) {
                final MetricRegistry metricRegistry = new MetricRegistry();
                metricRegistry.addListener(new JdbcConnectionPoolMetricsListener());
                poolConfig.setMetricRegistry(metricRegistry);
            }
        }
    }

}
