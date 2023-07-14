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
package io.helidon.reactive.dbclient.metrics.jdbc;

import java.lang.System.Logger.Level;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;

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
    private static final System.Logger LOGGER = System.getLogger(DropwizardMetricsListener.class.getName());

    private final String prefix;
    // Helidon metrics registry
    private final LazyValue<MetricRegistry> registry = LazyValue.create(
            () -> RegistryFactory.getInstance().getRegistry(Registry.VENDOR_SCOPE));

    private DropwizardMetricsListener(String prefix) {
        this.prefix = prefix;
    }

    static MetricRegistryListener create(Config config) {
        return new DropwizardMetricsListener(config.get("name-prefix").asString().orElse("db.pool."));
    }

    @Override
    public void onGaugeAdded(String name, Gauge<?> gauge) {
        Object value = gauge.getValue();
            if (value instanceof Number) {
                LOGGER.log(Level.TRACE, () -> String.format("Gauge added: %s", name));
                org.eclipse.microprofile.metrics.Gauge<?> mpGauge = new JdbcMetricsGauge<>((Gauge<? extends Number>) gauge);
                registry.get().gauge(prefix + name, mpGauge::getValue);
            } else {
                LOGGER.log(Level.WARNING, () -> String.format("Cannot add gauge returning type "
                                                                      + value.getClass().getName()
                                                                      + " which does not extend Number"));
            }
    }

    @Override
    public void onGaugeRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Gauge removed: %s", name));
        registry.get().remove(prefix + name);
    }

    @Override
    public void onCounterAdded(String name, Counter counter) {
        LOGGER.log(Level.TRACE, () -> String.format("Counter added: %s", name));
        registry.get().gauge(prefix + name, counter::getCount);
    }

    @Override
    public void onCounterRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Counter removed: %s", name));
        registry.get().remove(prefix + name);
    }

    // TODO can we support this in some way?
    @Override
    public void onHistogramAdded(String name, Histogram histogram) {
//        LOGGER.log(Level.TRACE, () -> String.format("Histogram added: %s", name));
//        registry.get().register(prefix + name, new JdbcMetricsHistogram(histogram));
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram added: %s", name));
    }

    // TODO can we support this in some way?
    @Override
    public void onHistogramRemoved(String name) {
//        LOGGER.log(Level.TRACE, () -> String.format("Histogram removed: %s", name));
//        registry.get().remove(prefix + name);
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram removed: %s", name));
    }

    // TODO can we support this in some way?
    @Override
    public void onMeterAdded(String name, Meter meter) {
//        LOGGER.log(Level.TRACE, () -> String.format("Meter added: %s", name));
//        registry.get().register(prefix + name, new JdbcMetricsMeter(meter));
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring meter added: %s", name));
    }

    @Override
    // TODO can we support this in some way?
    public void onMeterRemoved(String name) {
//        LOGGER.log(Level.TRACE, () -> String.format("Meter removed: %s", name));
//        registry.get().remove(prefix + name);
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring meter removed: %s", name));
    }

    @Override
    // TODO can we support this in some way?
    public void onTimerAdded(String name, Timer timer) {
//        LOGGER.log(Level.TRACE, () -> String.format("Timer added: %s", name));
//        registry.get().register(prefix + name, new JdbcMetricsTimer(timer));
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring timer added: %s", name));
    }

    @Override
    // TODO can we support this in some way?
    public void onTimerRemoved(String name) {
//        LOGGER.log(Level.TRACE, () -> String.format("Timer removed: %s", name));
//        registry.get().remove(prefix + name);
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram removed: %s", name));
    }

}
