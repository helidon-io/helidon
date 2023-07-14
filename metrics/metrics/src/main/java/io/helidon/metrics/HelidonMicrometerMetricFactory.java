/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.RegistrySettings;
import io.helidon.metrics.api.spi.MetricFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Helidon-specific implementation of metric factory methods.
 */
class HelidonMicrometerMetricFactory implements MetricFactory {

    static HelidonMicrometerMetricFactory create(RegistrySettings registrySettings) {

        // Make sure there is a Prometheus meter registry present in the global registry; add one if needed.
        if (Metrics.globalRegistry.getRegistries()
                .stream()
                .noneMatch(PrometheusMeterRegistry.class::isInstance)) {
            Metrics.globalRegistry.add(new PrometheusMeterRegistry(registrySettings::value));
        }

        return new HelidonMicrometerMetricFactory(Metrics.globalRegistry);
    }

    static HelidonMicrometerMetricFactory create(MeterRegistry meterRegistry) {
        return new HelidonMicrometerMetricFactory(meterRegistry);
    }

    private final MeterRegistry meterRegistry;

    HelidonMicrometerMetricFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Counter counter(String scope, Metadata metadata, Tag... tags) {
        return HelidonCounter.create(meterRegistry, scope, metadata, tags);
    }

    @Override
    public Timer timer(String scope, Metadata metadata, Tag... tags) {
        return HelidonTimer.create(meterRegistry, scope, metadata, tags);
    }

    @Override
    public Histogram summary(String scope, Metadata metadata, Tag... tags) {
        return HelidonHistogram.create(meterRegistry, scope, metadata, tags);
    }

    @Override
    public <N extends Number> Gauge<N> gauge(String scope, Metadata metadata, Supplier<N> supplier, Tag... tags) {
        return HelidonGauge.create(meterRegistry, scope, metadata, supplier, tags);
    }

    @Override
    public <N extends Number, T> Gauge<N> gauge(String scope, Metadata metadata, T target, Function<T, N> fn, Tag... tags) {
        return HelidonGauge.create(meterRegistry, scope, metadata, target, fn, tags);
    }

    @Override
    public <T> Gauge<Double> gauge(String scope, Metadata metadata, T target, ToDoubleFunction<T> fn, Tag... tags) {
        return HelidonGauge.create(meterRegistry, scope, metadata, target, fn, tags);
    }

    @Override
    public <T> Counter counter(String scope, Metadata metadata, T origin, ToDoubleFunction<T> function, Tag... tags) {
        return HelidonFunctionalCounter.create(meterRegistry, scope, metadata, origin, function, tags);
    }
}
