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

import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Hikari CP to Helidon metrics mapper.
 *
 * Listeners for events from the metrics registry and (un)registers metrics instances in Helidon.
 */
public class DropwizardMetricsListener implements MetricRegistryListener {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(DropwizardMetricsListener.class.getName());

    private final String prefix;
    // Helidon metrics registry
    private final MetricRegistry registry;

    private DropwizardMetricsListener(String prefix) {
        this.prefix = prefix;
        this.registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);
    }

    static MetricRegistryListener create(Config config) {
        return new DropwizardMetricsListener(config.get("name-prefix").asString().orElse("db.pool."));
    }

    @Override
    public void onGaugeAdded(String name, Gauge<?> gauge) {
        LOGGER.finest(() -> String.format("Gauge added: %s", name));
        registry.register(prefix + name, new JdbcMetricsGauge<>(gauge));
    }

    @Override
    public void onGaugeRemoved(String name) {
        LOGGER.finest(() -> String.format("Gauge removed: %s", name));
        registry.remove(prefix + name);
    }

    @Override
    public void onCounterAdded(String name, Counter counter) {
        LOGGER.finest(() -> String.format("Counter added: %s", name));
        registry.register(prefix + name, new JdbcMetricsCounter(counter));
    }

    @Override
    public void onCounterRemoved(String name) {
        LOGGER.finest(() -> String.format("Counter removed: %s", name));
        registry.remove(prefix + name);
    }

    @Override
    public void onHistogramAdded(String name, Histogram histogram) {
        LOGGER.finest(() -> String.format("Histogram added: %s", name));
        registry.register(prefix + name, new JdbcMetricsHistogram(histogram));
    }

    @Override
    public void onHistogramRemoved(String name) {
        LOGGER.finest(() -> String.format("Histogram removed: %s", name));
        registry.remove(prefix + name);
    }

    @Override
    public void onMeterAdded(String name, Meter meter) {
        LOGGER.finest(() -> String.format("Meter added: %s", name));
        registry.register(prefix + name, new JdbcMetricsMeter(meter));
    }

    @Override
    public void onMeterRemoved(String name) {
        LOGGER.finest(() -> String.format("Meter removed: %s", name));
        registry.remove(prefix + name);
    }

    @Override
    public void onTimerAdded(String name, Timer timer) {
        LOGGER.finest(() -> String.format("Timer added: %s", name));
        registry.register(prefix + name, new JdbcMetricsTimer(timer));
    }

    @Override
    public void onTimerRemoved(String name) {
        LOGGER.finest(() -> String.format("Timer removed: %s", name));
        registry.remove(prefix + name);
    }

}
