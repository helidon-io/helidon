/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.helidon.metrics.api.AbstractRegistry;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Metrics registry.
 */
public class Registry extends AbstractRegistry<HelidonMetric> {

    /**
     * Create a registry of a certain type.
     *
     * @param type Registry type.
     * @return The newly created registry.
     */
    public static Registry create(Type type) {
        return new Registry(type);
    }

    @Override
    protected <T extends Metric> HelidonMetric toImpl(Metadata metadata, T metric) {

        MetricType metricType = deriveType(metadata.getTypeRaw(), metric);
        switch (metricType) {
            case COUNTER:
                return HelidonCounter.create(registryType().getName(), metadata, (Counter) metric);
            case GAUGE:
                return HelidonGauge.create(registryType().getName(), metadata, (Gauge<?>) metric);
            case HISTOGRAM:
                return HelidonHistogram.create(registryType().getName(), metadata, (Histogram) metric);
            case METERED:
                return HelidonMeter.create(registryType().getName(), metadata, (Meter) metric);
            case TIMER:
                return HelidonTimer.create(registryType().getName(), metadata, (Timer) metric);
            case SIMPLE_TIMER:
                return HelidonSimpleTimer.create(registryType().getName(), metadata, (SimpleTimer) metric);
            case CONCURRENT_GAUGE:
                return HelidonConcurrentGauge.create(registryType().getName(), metadata, (ConcurrentGauge) metric);
            case INVALID:
            default:
                throw new IllegalArgumentException("Unexpected metric type " + metricType
                        + ": " + metric.getClass().getName());
        }
    }

    protected Registry(Type type) {
        super(type);
    }

    @Override
    protected Map<Class<? extends HelidonMetric>, MetricType> prepareMetricToTypeMap() {
        return Map.of(
                HelidonConcurrentGauge.class, MetricType.CONCURRENT_GAUGE,
                HelidonCounter.class, MetricType.COUNTER,
                HelidonGauge.class, MetricType.GAUGE,
                HelidonHistogram.class, MetricType.HISTOGRAM,
                HelidonMeter.class, MetricType.METERED,
                HelidonTimer.class, MetricType.TIMER,
                HelidonSimpleTimer.class, MetricType.SIMPLE_TIMER);
    }

    @Override
    protected Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> prepareMetricFactories() {
        // Omit gauge because creating a gauge requires an existing delegate instance.
        // These factory methods do not use delegates.
        return Map.of(MetricType.COUNTER, HelidonCounter::create,
                MetricType.HISTOGRAM, HelidonHistogram::create,
                MetricType.METERED, HelidonMeter::create,
                MetricType.TIMER, HelidonTimer::create,
                MetricType.SIMPLE_TIMER, HelidonSimpleTimer::create,
                MetricType.CONCURRENT_GAUGE, HelidonConcurrentGauge::create);
    }

    // -- declarations which let us keep the methods in the superclass protected; we do not want them public.
    @Override
    protected Optional<Map.Entry<MetricID, HelidonMetric>> getOptionalMetricEntry(String metricName) {
        return super.getOptionalMetricEntry(metricName);
    }

    @Override
    protected Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> metricFactories() {
        return super.metricFactories();
    }

    @Override
    protected Stream<Map.Entry<MetricID, HelidonMetric>> stream() {
        return super.stream();
    }

    @Override
    protected List<MetricID> metricIDsForName(String metricName) {
        return super.metricIDsForName(metricName);
    }

    @Override
    protected List<Map.Entry<MetricID, HelidonMetric>> getMetricsByName(String metricName) {
        return super.getMetricsByName(metricName);
    }
}
