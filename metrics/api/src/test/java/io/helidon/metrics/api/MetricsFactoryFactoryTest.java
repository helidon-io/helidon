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
package io.helidon.metrics.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class MetricsFactoryFactoryTest {
    private static final Config ROOT_CONFIG = Config.just(ConfigSources.create(Map.of(
            "metrics.app-name", "metrics-app",
            "server.features.observe.observers.metrics.app-name", "observe-app")));

    @Test
    void reusesCurrentMetricsFactory() {
        MetricsFactory currentFactory = Services.get(MetricsFactory.class);
        MetricsFactory serviceResolvedFactory = Services.get(MetricsFactory.class);

        assertThat(serviceResolvedFactory, sameInstance(currentFactory));
        assertThat(Services.get(MetricsFactory.class), sameInstance(currentFactory));
    }

    @Test
    @SuppressWarnings("removal")
    void legacyGlobalRegistryAccessReturnsServiceRegistryInstance() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = Services.get(MeterRegistry.class);

        assertThat(metricsFactory.globalRegistry(), sameInstance(meterRegistry));
        assertThat(metricsFactory.globalRegistry(MetricsConfig.create()), sameInstance(meterRegistry));
    }

    @Test
    void directFactoryCreatesIndependentInstances() {
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG, Services.get(ServiceRegistry.class));

        MetricsFactory firstFactory = serviceFactory.get();
        MetricsFactory secondFactory = serviceFactory.get();

        assertThat(secondFactory, not(sameInstance(firstFactory)));
    }

    @Test
    void directFactoryUsesObserveMetricsConfig() {
        MetricsFactory metricsFactory = new MetricsFactoryFactory(ROOT_CONFIG, Services.get(ServiceRegistry.class)).get();

        assertThat(metricsFactory.metricsConfig().appName().orElseThrow(), is("observe-app"));
    }

    @Test
    void factoryReceivesRootConfig() {
        MetricsFactoryProvider provider = (rootConfig, _, _) -> {
            assertThat(rootConfig.get("metrics.app-name").asString().get(), is("metrics-app"));
            assertThat(rootConfig.get("server.features.observe.observers.metrics.app-name").asString().get(),
                       is("observe-app"));
            return new NoOpMetricsFactory();
        };
        ServiceRegistryManager manager = testRegistryManager(provider);
        try {
            new MetricsFactoryFactory(ROOT_CONFIG, manager.registry()).get();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void deprecatedConfigInstanceRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> MetricsFactory.getInstance((Config) null));
    }

    @Test
    void registryAwareFactoryProviderRejectsNullArguments() {
        MetricsFactoryProvider provider = (rootConfig, metricsConfig, metersProviders) -> new NoOpMetricsFactory();
        MetricsConfig metricsConfig = MetricsConfig.create();
        List<MetersProvider> metersProviders = List.of();
        ServiceRegistry serviceRegistry = Services.get(ServiceRegistry.class);

        assertThrows(NullPointerException.class,
                     () -> provider.create(null, metricsConfig, metersProviders, serviceRegistry));
        assertThrows(NullPointerException.class,
                     () -> provider.create(Config.empty(), null, metersProviders, serviceRegistry));
        assertThrows(NullPointerException.class,
                     () -> provider.create(Config.empty(), metricsConfig, null, serviceRegistry));
        assertThrows(NullPointerException.class,
                     () -> provider.create(Config.empty(), metricsConfig, metersProviders, null));
    }

    @Test
    void noOpRegistryRejectsNullMeterEnablementArguments() {
        MeterRegistry meterRegistry = new NoOpMetricsFactory().globalRegistry();

        assertThrows(NullPointerException.class, () -> meterRegistry.isMeterEnabled(null, Map.of()));
        assertThrows(NullPointerException.class, () -> meterRegistry.isMeterEnabled("test", null));
    }

    @SuppressWarnings("removal")
    @Test
    void noOpRegistryUsesDefaultScopedEnablementBridge() {
        MeterRegistry meterRegistry = new NoOpMetricsFactory().globalRegistry();

        assertThat(meterRegistry.isMeterEnabled("test", Map.of(), Optional.of("ignored")), is(true));
        assertThrows(NullPointerException.class, () -> meterRegistry.isMeterEnabled("test", Map.of(), null));
    }

    @Test
    void noOpRegistryAppliesMeterBuilderCustomizersOnceUsingBuilderOrigin() {
        AtomicInteger basicCustomizationCount = new AtomicInteger();
        AtomicInteger originCustomizationCount = new AtomicInteger();
        MeterBuilderCustomizer customizer = new MeterBuilderCustomizer() {
            @Override
            public void customize(Meter.Builder<?, ?> builder) {
                var origin = builder.origin();
                if (origin.isPresent()) {
                    originCustomizationCount.incrementAndGet();
                    builder.addTag(new NoOpTag("origin", origin.get()));
                } else {
                    basicCustomizationCount.incrementAndGet();
                    builder.addTag(new NoOpTag("customized", "basic"));
                }
            }
        };
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .discoverServices(false)
                                                                                .discoverServicesFromServiceLoader(false)
                                                                                .putContractInstance(MeterBuilderCustomizer.class,
                                                                                                     customizer)
                                                                                .build());
        try {
            MetricsFactory metricsFactory = new MetricsFactoryFactory(Config.empty(), manager.registry()).get();
            MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());

            Counter basicCounter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("customized-counter"));
            Counter firstOriginCounter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("same-counter")
                                                                           .origin(FirstOrigin.class.getName()));
            Counter secondOriginCounter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("same-counter")
                                                                            .origin(SecondOrigin.class.getName()));

            assertThat("Basic customization count", basicCustomizationCount.get(), is(1));
            assertThat("Origin customization count", originCustomizationCount.get(), is(2));
            assertThat(basicCounter.id().tagsMap(), hasEntry("customized", "basic"));
            assertThat(firstOriginCounter.id().tagsMap(), hasEntry("origin", FirstOrigin.class.getName()));
            assertThat(secondOriginCounter.id().tagsMap(), hasEntry("origin", SecondOrigin.class.getName()));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void noOpBuilderRejectsNullOrigin() {
        MetricsFactory metricsFactory = new NoOpMetricsFactory();

        assertThrows(NullPointerException.class, () -> metricsFactory.counterBuilder("test").origin(null));
    }

    @Test
    void originDefaultsPreserveLegacyBuilderCompatibility() {
        Counter.Builder legacyBuilder = (Counter.Builder) Proxy.newProxyInstance(
                Counter.Builder.class.getClassLoader(),
                new Class<?>[] {Counter.Builder.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.toString());
                });

        assertThat(legacyBuilder.origin("ignored"), sameInstance(legacyBuilder));
        assertThat(legacyBuilder.origin(), is(Optional.empty()));
        assertThrows(NullPointerException.class, () -> legacyBuilder.origin(null));
    }

    @Test
    void preDestroyClosesCreatedFactories() {
        AtomicInteger nextFactory = new AtomicInteger();
        CloseTrackingMetricsFactory firstFactory = new CloseTrackingMetricsFactory();
        CloseTrackingMetricsFactory secondFactory = new CloseTrackingMetricsFactory();
        CloseTrackingMetricsFactory[] factories = {firstFactory, secondFactory};
        MetricsFactoryProvider provider = (_, _, _) -> factories[nextFactory.getAndIncrement()];
        ServiceRegistryManager manager = testRegistryManager(provider);
        try {
            MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG, manager.registry());

            serviceFactory.get();
            serviceFactory.get();

            serviceFactory.preDestroy();

            assertThat(firstFactory.closeCount(), is(1));
            assertThat(secondFactory.closeCount(), is(1));

            serviceFactory.preDestroy();

            assertThat(firstFactory.closeCount(), is(1));
            assertThat(secondFactory.closeCount(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void closeAllDoesNotAffectServicesLookup() {
        MetricsFactory firstFactory = Services.get(MetricsFactory.class);

        MetricsFactory.closeAll();
        MetricsFactory secondFactory = Services.get(MetricsFactory.class);

        assertThat(secondFactory, sameInstance(firstFactory));
    }

    private static ServiceRegistryManager testRegistryManager(MetricsFactoryProvider provider) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .discoverServices(false)
                                                     .discoverServicesFromServiceLoader(false)
                                                     .putContractInstance(MetricsFactoryProvider.class, provider)
                                                     .build());
    }

    private static class CloseTrackingMetricsFactory extends NoOpMetricsFactory {
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
        }

        private int closeCount() {
            return closeCount;
        }
    }

    private static class FirstOrigin {
    }

    private static class SecondOrigin {
    }
}
