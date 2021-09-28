/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Implementation of {@link MetricRegistry} which returns no-op metrics implementations.
 */
class NoOpMetricRegistry extends AbstractRegistry<NoOpMetric> {

    public static NoOpMetricRegistry create(Type type) {
        return new NoOpMetricRegistry(type);
    }

    private NoOpMetricRegistry(Type type) {
        super(type);
    }

    @Override
    protected Map<MetricType, BiFunction<String, Metadata, NoOpMetric>> prepareMetricFactories() {
        return Map.of(MetricType.COUNTER, NoOpMetric.NoOpCounter::create,
                MetricType.HISTOGRAM, NoOpMetric.NoOpHistogram::create,
                MetricType.METERED, NoOpMetric.NoOpMeter::create,
                MetricType.TIMER, NoOpMetric.NoOpTimer::create,
                MetricType.SIMPLE_TIMER, NoOpMetric.NoOpSimpleTimer::create,
                MetricType.CONCURRENT_GAUGE, NoOpMetric.NoOpConcurrentGauge::create);
    }

    @Override
    protected <T extends Metric> NoOpMetric toImpl(Metadata metadata, T metric) {
        String registryTypeName = registryType().getName();
        MetricType metricType = deriveType(metadata.getTypeRaw(), metric);
        switch (metricType) {
            case COUNTER:
                return NoOpMetric.NoOpCounter.create(registryTypeName, metadata);
            case GAUGE:
                return NoOpMetric.NoOpGauge.create(registryTypeName, metadata, (Gauge<?>) metric);
            case HISTOGRAM:
                return  NoOpMetric.NoOpHistogram.create(registryTypeName, metadata);
            case METERED:
                return NoOpMetric.NoOpMeter.create(registryTypeName, metadata);
            case TIMER:
                return NoOpMetric.NoOpTimer.create(registryTypeName, metadata);
            case SIMPLE_TIMER:
                return NoOpMetric.NoOpSimpleTimer.create(registryTypeName, metadata);
            case CONCURRENT_GAUGE:
                return NoOpMetric.NoOpConcurrentGauge.create(registryTypeName, metadata);
            case INVALID:
            default:
                throw new IllegalArgumentException("Unexpected metric type " + metricType
                        + ": " + metric.getClass().getName());
        }
    }

    @Override
    protected Map<Class<? extends NoOpMetric>, MetricType> prepareMetricToTypeMap() {
        return Map.of(NoOpMetric.NoOpConcurrentGauge.class, MetricType.CONCURRENT_GAUGE,
                      NoOpMetric.NoOpCounter.class, MetricType.COUNTER,
                      NoOpMetric.NoOpGauge.class, MetricType.GAUGE,
                      NoOpMetric.NoOpHistogram.class, MetricType.HISTOGRAM,
                      NoOpMetric.NoOpMeter.class, MetricType.METERED,
                      NoOpMetric.NoOpTimer.class, MetricType.TIMER,
                      NoOpMetric.NoOpSimpleTimer.class, MetricType.SIMPLE_TIMER);
    }
}
