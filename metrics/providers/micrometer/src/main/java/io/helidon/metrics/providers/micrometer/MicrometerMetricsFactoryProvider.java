/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Provides the Micrometer meter registry to use as a delegate for the implementation of the Helidon metrics API.
 */
public class MicrometerMetricsFactoryProvider implements MetricsFactoryProvider {

    private final List<MicrometerMetricsFactory> metricsFactories = new ArrayList<>();

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} based on Micrometer. Public for service loading.
     */
    public MicrometerMetricsFactoryProvider() {
        observeGlobalRegistry();
        addSystemTagsFilter();
    }

    @Override
    public MetricsFactory create(Config rootConfig, MetricsConfig metricsConfig, Collection<MetersProvider> metersProviders) {
        return save(MicrometerMetricsFactory.create(rootConfig, metricsConfig, metersProviders));
    }

    @Override
    public void close() {
        var toHandle = List.copyOf(metricsFactories);
        toHandle.forEach(MetricsFactory::close);
        metricsFactories.clear();
        List<Meter> meters = List.copyOf(Metrics.globalRegistry.getMeters());
        meters.forEach(Metrics.globalRegistry::remove);
    }

    private MicrometerMetricsFactory save(MicrometerMetricsFactory metricsFactory) {
        metricsFactories.add(metricsFactory);
        return metricsFactory;
    }

    private void onMeterAdded(Meter meter) {
        metricsFactories.forEach(mf -> mf.onMeterAdded(meter));
    }

    private void onMeterRemoved(Meter meter) {
        metricsFactories.forEach(mf -> mf.onMeterRemoved(meter));
    }

    private void observeGlobalRegistry() {
        Metrics.globalRegistry.config().onMeterAdded(this::onMeterAdded);
        Metrics.globalRegistry.config().onMeterRemoved(this::onMeterRemoved);
    }

    private void addSystemTagsFilter() {
        Metrics.globalRegistry
                .config()
                .meterFilter(MeterFilter.commonTags(SystemTagsMeterFilterManager
                                                            .instance()
                                                            .tags()));
    }
}
