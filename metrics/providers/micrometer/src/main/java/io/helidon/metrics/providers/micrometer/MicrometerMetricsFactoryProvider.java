/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Provides the Micrometer meter registry to use as a delegate for the implementation of the Helidon metrics API.
 */
public class MicrometerMetricsFactoryProvider implements MetricsFactoryProvider {

    /*
     * Micrometer's global registry accepts listeners but does not expose a way to remove them. Keep one JVM-level
     * callback bridge, but only weakly reference Helidon factories so service registry shutdown releases owned state.
     */
    private static final GlobalRegistryObserver GLOBAL_REGISTRY_OBSERVER = new GlobalRegistryObserver();
    private static final ThreadLocal<List<io.micrometer.core.instrument.Tag>> CURRENT_SYSTEM_TAGS = new ThreadLocal<>();

    private final List<MicrometerMetricsFactory> metricsFactories = new CopyOnWriteArrayList<>();

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public MicrometerMetricsFactoryProvider() {
        GLOBAL_REGISTRY_OBSERVER.configure();
    }

    @Override
    public MetricsFactory create(Config rootConfig, MetricsConfig metricsConfig, Collection<MetersProvider> metersProviders) {
        return save(MicrometerMetricsFactory.create(metricsConfig, metersProviders, this::remove));
    }

    @Override
    public void close() {
        List.copyOf(metricsFactories).forEach(MetricsFactory::close);
        metricsFactories.clear();
    }

    private MicrometerMetricsFactory save(MicrometerMetricsFactory metricsFactory) {
        metricsFactories.add(metricsFactory);
        GLOBAL_REGISTRY_OBSERVER.add(metricsFactory);
        return metricsFactory;
    }

    private void remove(MicrometerMetricsFactory metricsFactory) {
        metricsFactories.remove(metricsFactory);
        GLOBAL_REGISTRY_OBSERVER.remove(metricsFactory);
    }

    static <T> T withSystemTags(Map<String, String> systemTags, Supplier<T> registration) {
        List<io.micrometer.core.instrument.Tag> previousTags = CURRENT_SYSTEM_TAGS.get();
        List<io.micrometer.core.instrument.Tag> currentTags = new ArrayList<>();
        systemTags.forEach((name, value) -> currentTags.add(io.micrometer.core.instrument.Tag.of(name, value)));
        CURRENT_SYSTEM_TAGS.set(currentTags);
        try {
            return registration.get();
        } finally {
            if (previousTags == null) {
                CURRENT_SYSTEM_TAGS.remove();
            } else {
                CURRENT_SYSTEM_TAGS.set(previousTags);
            }
        }
    }

    private static class GlobalRegistryObserver {
        private final AtomicBoolean configured = new AtomicBoolean();
        private final List<WeakReference<MicrometerMetricsFactory>> metricsFactories = new CopyOnWriteArrayList<>();

        private void configure() {
            if (configured.compareAndSet(false, true)) {
                Metrics.globalRegistry.config().onMeterAdded(this::onMeterAdded);
                Metrics.globalRegistry.config().onMeterRemoved(this::onMeterRemoved);
                Metrics.globalRegistry.config().meterFilter(new MeterFilter() {
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        List<io.micrometer.core.instrument.Tag> tags = CURRENT_SYSTEM_TAGS.get();
                        if (tags == null || tags.isEmpty()) {
                            return id;
                        }
                        return id.replaceTags(Tags.concat(tags, id.getTagsAsIterable()));
                    }
                });
            }
        }

        private void add(MicrometerMetricsFactory metricsFactory) {
            metricsFactories.add(new WeakReference<>(metricsFactory));
        }

        private void remove(MicrometerMetricsFactory metricsFactory) {
            metricsFactories.removeIf(ref -> {
                MicrometerMetricsFactory found = ref.get();
                return found == null || found == metricsFactory;
            });
        }

        private void onMeterAdded(Meter meter) {
            liveFactories().forEach(mf -> mf.onMeterAdded(meter));
        }

        private void onMeterRemoved(Meter meter) {
            liveFactories().forEach(mf -> mf.onMeterRemoved(meter));
        }

        private List<MicrometerMetricsFactory> liveFactories() {
            List<MicrometerMetricsFactory> result = new ArrayList<>();
            metricsFactories.removeIf(ref -> {
                MicrometerMetricsFactory metricsFactory = ref.get();
                if (metricsFactory == null) {
                    return true;
                }
                result.add(metricsFactory);
                return false;
            });
            return result;
        }
    }
}
