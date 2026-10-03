/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated("Tests global service registry initialization")
class TestHelidonProviderSelection {

    @Test
    void serviceRegistryUsesHelidonProviderWhenOnlyHelidonProviderIsPresent() {
        assertThat(Services.get(MetricsFactory.class), instanceOf(HelidonMetricsFactory.class));
    }

    @Test
    void defaultFactoryDoesNotInitializeGlobalServiceRegistry() {
        withoutGlobalRegistry(() -> {
            HelidonMetricsFactory factory = HelidonMetricsFactory.create();
            try {
                assertThat("Constructing a default factory does not initialize global services",
                           GlobalServiceRegistry.configured(), is(false));
                MeterRegistry registry = factory.globalRegistry();
                Counter counter = registry.getOrCreate(factory.counterBuilder("independent.counter"));
                counter.increment(2);

                assertThat(counter.count(), is(2L));
                assertThat("Using a default factory does not initialize global services",
                           GlobalServiceRegistry.configured(), is(false));
            } finally {
                factory.close();
            }
        });
    }

    @Test
    void registryFactoryDoesNotInitializeGlobalServiceRegistry() {
        withoutGlobalRegistry(() -> {
            Config config = Config.just(ConfigSources.create(Map.of("metrics.app-name", "isolated-app")));
            ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .putContractInstance(Config.class, config)
                    .build());
            try {
                MetricsFactory factory = manager.registry().get(MetricsFactory.class);
                MeterRegistry registry = manager.registry().get(MeterRegistry.class);
                Counter counter = registry.getOrCreate(factory.counterBuilder("isolated.counter"));
                counter.increment(3);

                assertThat("Factory uses its owning service registry configuration",
                           factory.metricsConfig().appName().orElseThrow(), is("isolated-app"));
                assertThat(counter.count(), is(3L));
                assertThat("An isolated service registry does not initialize global services",
                           GlobalServiceRegistry.configured(), is(false));
            } finally {
                manager.shutdown();
            }
            assertThat("Shutting down an isolated factory does not initialize global services",
                       GlobalServiceRegistry.configured(), is(false));
        });
    }

    @Test
    void serviceRegistriesOwnIndependentFactories() {
        Config firstConfig = Config.just(ConfigSources.create(Map.of("metrics.app-name", "first-app")));
        Config secondConfig = Config.just(ConfigSources.create(Map.of("metrics.app-name", "second-app")));
        ServiceRegistryManager first = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                            .putContractInstance(Config.class, firstConfig)
                                                                            .build());
        ServiceRegistryManager second = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                             .putContractInstance(Config.class, secondConfig)
                                                                             .build());
        try {
            MetricsFactory firstFactory = first.registry().get(MetricsFactory.class);
            MetricsFactory secondFactory = second.registry().get(MetricsFactory.class);
            MeterRegistry firstRegistry = first.registry().get(MeterRegistry.class);
            MeterRegistry secondRegistry = second.registry().get(MeterRegistry.class);
            Counter firstCounter = firstRegistry.getOrCreate(firstFactory.counterBuilder("same.name"));
            Counter secondCounter = secondRegistry.getOrCreate(secondFactory.counterBuilder("same.name"));
            firstCounter.increment(2);
            secondCounter.increment(3);

            first.shutdown();

            assertThrows(IllegalStateException.class, firstFactory::globalRegistry);
            assertThat("First meter is deleted", firstRegistry.isDeleted(firstCounter), is(true));
            assertThat("Second service registry retains its own configuration",
                       secondFactory.metricsConfig().appName().orElseThrow(), is("second-app"));
            assertThat("Second service registry retains its registry",
                       secondFactory.globalRegistry(), sameInstance(secondRegistry));
            secondCounter.increment();
            assertThat("Second service registry continues recording", secondCounter.count(), is(4L));
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void serviceRegistrySuppliesCustomizersAndLifecycleListeners() {
        AtomicInteger meterAdditions = new AtomicInteger();
        MeterRegistryLifeCycleListener listener =
                (registry, _) -> registry.onMeterAdded(_ -> meterAdditions.incrementAndGet());
        MeterBuilderCustomizer customizer =
                builder -> builder.origin().ifPresent(origin -> builder.addTag(new HelidonTag("origin", origin)));
        MetersProvider metersProvider = factory -> List.of(factory.counterBuilder("provided.counter"));
        String providerOrigin = metersProvider.getClass().getName();
        ServiceRegistryManager manager = ServiceRegistryManager.create(
                ServiceRegistryConfig.builder()
                        .putContractInstance(MeterBuilderCustomizer.class, customizer)
                        .putContractInstance(MeterRegistryLifeCycleListener.class, listener)
                        .putContractInstance(MetersProvider.class, metersProvider)
                        .build());
        try {
            MetricsFactory factory = manager.registry().get(MetricsFactory.class);
            MeterRegistry registry = manager.registry().get(MeterRegistry.class);
            Counter provided = registry.counter("provided.counter", List.of(factory.tagCreate("origin", providerOrigin)))
                    .orElseThrow();
            Counter first = registry.getOrCreate(factory.counterBuilder("customized.counter").origin("first"));
            Counter second = registry.getOrCreate(factory.counterBuilder("customized.counter").origin("second"));
            first.increment(2);
            second.increment(3);

            assertThat("Provider meter customized", provided.id().tagsMap().get("origin"), is(providerOrigin));
            assertThat("First origin records independently", first.count(), is(2L));
            assertThat("Second origin records independently", second.count(), is(3L));
            assertThat("Customizer runs before lookup",
                       registry.getOrCreate(factory.counterBuilder("customized.counter").origin("first")),
                       sameInstance(first));
            assertThat("Lifecycle listener sees provider and application meters", meterAdditions.get(), is(3));
        } finally {
            manager.shutdown();
        }
    }

    private static void withoutGlobalRegistry(Runnable action) {
        ServiceRegistry original = GlobalServiceRegistry.configured() ? GlobalServiceRegistry.registry() : null;
        resetGlobalRegistry();
        try {
            assertThat("Test starts without a global service registry", GlobalServiceRegistry.configured(), is(false));
            action.run();
        } finally {
            resetGlobalRegistry();
            if (original != null) {
                GlobalServiceRegistry.registry(original);
            }
        }
    }

    private static void resetGlobalRegistry() {
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        manager.shutdown();
    }
}
