/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;

/**
 * Hikari CP to Helidon metrics mapper.
 * <br/>
 * Listeners for events from the metrics registry and (un)registers metrics instances in Helidon.
 */
public class DropwizardMetricsListener implements MetricRegistryListener {

    private static final String SCOPE = io.helidon.metrics.api.Meter.Scope.VENDOR;

    private static final System.Logger LOGGER = System.getLogger(DropwizardMetricsListener.class.getName());

    private final String prefix;
    private final LazyValue<MeterRegistry> registry = LazyValue.create(Metrics::globalRegistry);

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
            @SuppressWarnings("unchecked")
            Gauge<? extends Number> nGauge = (Gauge<? extends Number>) gauge;
            registerGauge(name, nGauge);
        } else {
            LOGGER.log(Level.WARNING, () -> String.format("Cannot add gauge returning type "
                                                                  + value.getClass().getName()
                                                                  + " which does not extend Number"));
        }
    }

    @Override
    public void onGaugeRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Gauge removed: %s", name));
        registry.get().remove(prefix + name, Set.of(), SCOPE);
    }

    @Override
    public void onCounterAdded(String name, Counter counter) {
        LOGGER.log(Level.TRACE, () -> String.format("Counter added: %s", name));
        registerGauge(name, counter);
    }

    @Override
    public void onCounterRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Counter removed: %s", name));
        registry.get().remove(prefix + name, Set.of(), SCOPE);
    }

    @Override
    public void onHistogramAdded(String name, Histogram histogram) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram added: %s", name));
    }

    @Override
    public void onHistogramRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram removed: %s", name));
    }

    @Override
    public void onMeterAdded(String name, Meter meter) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring meter added: %s", name));
    }

    @Override
    public void onMeterRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring meter removed: %s", name));
    }

    @Override
    public void onTimerAdded(String name, Timer timer) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring timer added: %s", name));
    }

    @Override
    public void onTimerRemoved(String name) {
        LOGGER.log(Level.TRACE, () -> String.format("Ignoring histogram removed: %s", name));
    }


    private io.helidon.metrics.api.Gauge registerGauge(String name, Gauge<? extends Number> gauge) {
        return registry.get()
                .getOrCreate(io.helidon.metrics.api.Gauge.builder(prefix + name,
                                                                  gauge,
                                                                  g -> g.getValue().doubleValue())
                                     .scope(SCOPE));
    }

    private io.helidon.metrics.api.Gauge registerGauge(String name, Counter counter) {
        return registry.get()
                .getOrCreate(io.helidon.metrics.api.Gauge.builder(prefix + name,
                                                                  counter,
                                                                  Counter::getCount)
                                     .scope(SCOPE));
    }
}
