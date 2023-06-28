/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of {@link MetricRegistry} which returns no-op metrics implementations.
 */
class NoOpMetricRegistry extends AbstractRegistry {

    private static final RegistrySettings REGISTRY_SETTINGS = RegistrySettings.builder().enabled(false).build();

    private NoOpMetricRegistry(String scope) {
        super(scope, REGISTRY_SETTINGS, new NoOpMetricFactory());
    }

    public static NoOpMetricRegistry create(String scope) {
        return new NoOpMetricRegistry(scope);
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
    protected <T, R extends Number> Gauge<R> createGauge(Metadata metadata, T object, Function<T, R> func) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(scope(),
                                                   metadata,
                                                   () -> func.apply(object));
    }

    @Override
    protected <R extends Number> Gauge<R> createGauge(Metadata metadata, Supplier<R> supplier) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(scope(),
                                                   metadata,
                                                   supplier::get);
    }

    @Override
    protected void doRemove(MetricID metricId, HelidonMetric metric) {
        // The no-op registry does not have a delegate registry (such as Micrometer) to keep synchronized.
    }

    @Override
    public <T> Counter counter(Metadata metadata, T origin, ToDoubleFunction<T> function, Tag... tags) {
        return NoOpMetricImpl.NoOpFunctionalCounterImpl.create(scope(), metadata, origin, function, tags);
    }
}
