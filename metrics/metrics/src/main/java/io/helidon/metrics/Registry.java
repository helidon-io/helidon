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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        final Map<Class<? extends HelidonMetric>, MetricType> result = new HashMap<>();
        result.put(HelidonConcurrentGauge.class, MetricType.CONCURRENT_GAUGE);
        result.put(HelidonCounter.class, MetricType.COUNTER);
        result.put(HelidonGauge.class, MetricType.GAUGE);
        result.put(HelidonHistogram.class, MetricType.HISTOGRAM);
        result.put(HelidonMeter.class, MetricType.METERED);
        result.put(HelidonTimer.class, MetricType.TIMER);
        result.put(HelidonSimpleTimer.class, MetricType.SIMPLE_TIMER);
        return result;
    }

    @Override
    protected HelidonMetric newImpl(Metadata metadata) {
        String registryTypeName = registryType().getName();
        switch (metadata.getTypeRaw()) {
            case COUNTER:
                return HelidonCounter.create(registryTypeName, metadata);
            case GAUGE:
                throw new IllegalArgumentException("Attempt to create Gauge without an implementation");
            case HISTOGRAM:
                return HelidonHistogram.create(registryTypeName, metadata);
            case METERED:
                return HelidonMeter.create(registryTypeName, metadata);
            case TIMER:
                return HelidonTimer.create(registryTypeName, metadata);
            case SIMPLE_TIMER:
                return HelidonSimpleTimer.create(registryTypeName, metadata);
            case CONCURRENT_GAUGE:
                return HelidonConcurrentGauge.create(registryTypeName, metadata);
            case INVALID:
            default:
                throw new IllegalArgumentException("Unexpected metric type " + metadata.getType());
        }
    }

    // -- declarations which let us keep the methods in the superclass protected; we do not want them public.
    @Override
    protected Optional<Map.Entry<MetricID, HelidonMetric>> getOptionalMetricEntry(String metricName) {
        return super.getOptionalMetricEntry(metricName);
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

    @Override
    protected Map<Class<? extends HelidonMetric>, MetricType> metricToTypeMap() {
        return super.metricToTypeMap();
    }
}
