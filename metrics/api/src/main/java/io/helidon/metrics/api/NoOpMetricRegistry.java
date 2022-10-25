/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Implementation of {@link MetricRegistry} which returns no-op metrics implementations.
 */
class NoOpMetricRegistry extends AbstractRegistry {

    static final Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> NO_OP_METRIC_FACTORIES =
            Map.of(MetricType.COUNTER, NoOpMetricImpl.NoOpCounterImpl::create,
                   MetricType.GAUGE, NoOpMetricImpl.NoOpGaugeImpl::create,
                   MetricType.HISTOGRAM, NoOpMetricImpl.NoOpHistogramImpl::create,
                   MetricType.METERED, NoOpMetricImpl.NoOpMeterImpl::create,
                   MetricType.TIMER, NoOpMetricImpl.NoOpTimerImpl::create,
                   MetricType.SIMPLE_TIMER, NoOpMetricImpl.NoOpSimpleTimerImpl::create,
                   MetricType.CONCURRENT_GAUGE, NoOpMetricImpl.NoOpConcurrentGaugeImpl::create);

    private static final RegistrySettings REGISTRY_SETTINGS = RegistrySettings.builder().enabled(false).build();

    private NoOpMetricRegistry(MetricRegistry.Type type) {
        super(type, REGISTRY_SETTINGS);
    }

    public static NoOpMetricRegistry create(MetricRegistry.Type type) {
        return new NoOpMetricRegistry(type);
    }

    @Override
    public boolean enabled(String metricName) {
        return false;
    }

    @Override
    public Optional<MetricInstance> find(String metricName) {
        return Optional.empty();
    }

    @Override
    public List<MetricInstance> list(String metricName) {
        return List.of();
    }

    @Override
    public List<MetricID> metricIdsByName(String name) {
        return List.of();
    }

    @Override
    public Optional<MetricsForMetadata> metricsByName(String name) {
        return Optional.empty();
    }

    @Override
    protected HelidonMetric toImpl(Metadata metadata, Metric metric) {
        String registryTypeName = type();
        MetricType metricType = AbstractRegistry.deriveType(metadata.getTypeRaw(), metric);
        return switch (metricType) {
            case COUNTER -> NoOpMetricImpl.NoOpCounterImpl.create(registryTypeName, metadata);
            case GAUGE -> NoOpMetricImpl.NoOpGaugeImpl.create(registryTypeName, metadata, (Gauge<?>) metric);
            case HISTOGRAM -> NoOpMetricImpl.NoOpHistogramImpl.create(registryTypeName, metadata);
            case METERED -> NoOpMetricImpl.NoOpMeterImpl.create(registryTypeName, metadata);
            case TIMER -> NoOpMetricImpl.NoOpTimerImpl.create(registryTypeName, metadata);
            case SIMPLE_TIMER -> NoOpMetricImpl.NoOpSimpleTimerImpl.create(registryTypeName, metadata);
            case CONCURRENT_GAUGE -> NoOpMetricImpl.NoOpConcurrentGaugeImpl.create(registryTypeName, metadata);
            case INVALID -> throw new IllegalArgumentException("Unexpected metric type " + metricType
                                                                       + ": " + metric.getClass().getName());
        };
    }

    @Override
    protected Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> prepareMetricFactories() {
        return noOpMetricFactories();
    }

    @Override
    protected <T, R extends Number> Gauge<R> createGauge(Metadata metadata, T object, Function<T, R> func) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(type(),
                                                   metadata,
                                                   () -> func.apply(object));
    }

    @Override
    protected <R extends Number> Gauge<R> createGauge(Metadata metadata, Supplier<R> supplier) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(type(),
                                                   metadata,
                                                   () -> supplier.get());
    }

    @Override
    protected Map<Class<? extends HelidonMetric>, MetricType> prepareMetricToTypeMap() {
        return Map.of(NoOpMetricImpl.NoOpConcurrentGaugeImpl.class, MetricType.CONCURRENT_GAUGE,
                      NoOpMetricImpl.NoOpCounterImpl.class, MetricType.COUNTER,
                      NoOpMetricImpl.NoOpGaugeImpl.class, MetricType.GAUGE,
                      NoOpMetricImpl.NoOpHistogramImpl.class, MetricType.HISTOGRAM,
                      NoOpMetricImpl.NoOpMeterImpl.class, MetricType.METERED,
                      NoOpMetricImpl.NoOpTimerImpl.class, MetricType.TIMER,
                      NoOpMetricImpl.NoOpSimpleTimerImpl.class, MetricType.SIMPLE_TIMER);
    }

    protected static Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> noOpMetricFactories() {
        return NO_OP_METRIC_FACTORIES;
    }

}
