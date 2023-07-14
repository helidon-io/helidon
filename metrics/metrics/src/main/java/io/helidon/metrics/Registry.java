/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import io.helidon.metrics.api.AbstractRegistry;
import io.helidon.metrics.api.MetricInstance;
import io.helidon.metrics.api.RegistrySettings;

import io.micrometer.core.instrument.Metrics;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Metrics registry.
 */
public class Registry extends AbstractRegistry {

    private final AtomicReference<RegistrySettings> registrySettings = new AtomicReference<>();

    /**
     * Create a registry of a certain scope.
     *
     * @param scope Registry scope.
     * @param registrySettings Registry settings to use in creating the registry.
     * @return The newly created registry.
     */
    public static Registry create(String scope, RegistrySettings registrySettings) {
        return new Registry(scope, registrySettings);
    }

    @Override
    public void update(RegistrySettings registrySettings) {
        super.update(registrySettings);
        this.registrySettings.set(registrySettings);
    }

    @Override
    public <T> Counter counter(Metadata metadata, T origin, ToDoubleFunction<T> function, Tag... tags) {
        return metricStore().counter(metadata, origin, function, tags);
    }

    @Override
    public boolean enabled(String metricName) {
        return registrySettings.get().isMetricEnabled(metricName);
    }

    /**
     * Creates a new instance.
     *
     * @param scope registry scope for the new registry
     * @param registrySettings registry settings to influence the created registry
     */
    protected Registry(String scope, RegistrySettings registrySettings) {
        super(scope, registrySettings, HelidonMicrometerMetricFactory.create(registrySettings));
        this.registrySettings.set(registrySettings);
    }

    @Override
    protected <R extends Number> Gauge<R> createGauge(Metadata metadata, Supplier<R> supplier) {
        return HelidonGauge.create(scope(), metadata, supplier);
    }

    @Override
    protected <T, R extends Number> Gauge<R> createGauge(Metadata metadata,
                                                              T object,
                                                              Function<T, R> func) {
        return HelidonGauge.create(scope(), metadata, object, func);
    }

    @Override
    public Stream<MetricInstance> stream() {
        return super.stream();
    }

    @Override
    protected List<MetricID> metricIDsForName(String metricName) {
        return super.metricIDsForName(metricName);
    }

    @Override
    protected void doRemove(MetricID metricId, io.helidon.metrics.api.HelidonMetric metric) {
        Metrics.globalRegistry.remove(((HelidonMetric) metric).delegate());
    }
}
