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
package io.helidon.webserver.observe.metrics;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import io.helidon.config.Config;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.metrics.spi.AutoHttpMetricsProvider;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

class MetricsObserverRegistryTest {

    @Test
    void managedProviderUsesOwningRegistry() {
        MeterRegistry owningRegistry = Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder()
                                             .warnOnMultipleRegistries(false)
                                             .build());
        assertThat("Owning registry is distinct from the global registry",
                   owningRegistry == Services.get(MeterRegistry.class), is(false));
        var formatterRegistry = new AtomicReference<MeterRegistry>();
        var autoProviderInvocations = new AtomicInteger();
        MeterRegistryFormatterProvider formatterProvider = formatterProvider(formatterRegistry);
        AutoHttpMetricsProvider autoHttpMetricsProvider = config -> {
            autoProviderInvocations.incrementAndGet();
            return Optional.empty();
        };
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .discoverServices(false)
                                                                                .discoverServicesFromServiceLoader(false)
                                                                                .putContractInstance(MeterRegistry.class,
                                                                                                     owningRegistry)
                                                                                .putContractInstance(
                                                                                        MeterRegistryFormatterProvider.class,
                                                                                        formatterProvider)
                                                                                .putContractInstance(
                                                                                        AutoHttpMetricsProvider.class,
                                                                                        autoHttpMetricsProvider)
                                                                                .build());
        HttpRouting routing = null;
        try {
            Observer observer = new MetricsObserveProvider().create(Config.empty(), "metrics", manager.registry());
            routing = registerAndBuild(observer);

            assertThat("Formatter sees the owning meter registry",
                       formatterRegistry.get(), sameInstance(owningRegistry));
            assertThat("Owning automatic metrics provider is consulted", autoProviderInvocations.get(), is(1));
            assertThat("KPI meter is registered with the owning registry",
                       hasMeter(owningRegistry, "requests.count"), is(true));
            routing.afterStop();
            routing = null;
            assertThat("KPI meter is removed when routing stops",
                       hasMeter(owningRegistry, "requests.count"), is(false));
        } finally {
            if (routing != null) {
                routing.afterStop();
            }
            manager.shutdown();
            owningRegistry.close();
        }
    }

    @Test
    void explicitMeterRegistryTakesPrecedence() {
        MeterRegistry explicitRegistry = Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder()
                                             .warnOnMultipleRegistries(false)
                                             .build());
        var sharedRegistryLookups = new AtomicInteger();
        var formatterRegistry = new AtomicReference<MeterRegistry>();
        try {
            MetricsFeature metricsFeature = new MetricsFeature(MetricsObserverConfig.builder()
                                                                       .meterRegistry(explicitRegistry)
                                                                       .buildPrototype(),
                                                               () -> {
                                                                   sharedRegistryLookups.incrementAndGet();
                                                                   throw new AssertionError("Shared registry must not be resolved");
                                                               },
                                                               () -> List.of(formatterProvider(formatterRegistry)));

            assertThat("Explicit registry enables the endpoint", metricsFeature.enabled(), is(true));
            assertThat("Formatter sees the explicitly configured registry",
                       formatterRegistry.get(), sameInstance(explicitRegistry));
            assertThat("Shared registry supplier is not resolved", sharedRegistryLookups.get(), is(0));
        } finally {
            explicitRegistry.close();
        }
    }

    @Test
    void disabledManagedObserverDoesNotResolveDependencies() {
        var dependencyLookups = new AtomicInteger();
        MetricsObserver observer = new MetricsObserver(MetricsObserverConfig.builder()
                                                               .enabled(false)
                                                               .buildPrototype(),
                                                       () -> {
                                                           dependencyLookups.incrementAndGet();
                                                           throw new AssertionError("Meter registry must not be resolved");
                                                       },
                                                       () -> {
                                                           dependencyLookups.incrementAndGet();
                                                           throw new AssertionError("Formatter providers must not be resolved");
                                                       },
                                                       () -> {
                                                           dependencyLookups.incrementAndGet();
                                                           throw new AssertionError("Auto-metrics providers must not be resolved");
                                                       });

        observer.register(featureContext(HttpRouting.builder()),
                          List.of(HttpRouting.builder()),
                          UnaryOperator.identity());

        assertThat("Disabled observer leaves registry dependencies unresolved", dependencyLookups.get(), is(0));
    }

    @Test
    void directProviderRetainsGlobalFallback() {
        Observer observer = new MetricsObserveProvider().create(Config.empty(), "metrics");
        HttpRouting routing = registerAndBuild(observer);
        try {
            assertThat(routing, notNullValue());
        } finally {
            routing.afterStop();
        }
    }

    private static HttpRouting registerAndBuild(Observer observer) {
        HttpRouting.Builder observeRouting = HttpRouting.builder();
        observer.register(featureContext(HttpRouting.builder()),
                          List.of(observeRouting),
                          UnaryOperator.identity());
        return observeRouting.build();
    }

    private static ServerFeature.ServerFeatureContext featureContext(HttpRouting.Builder applicationRouting) {
        return new ServerFeature.ServerFeatureContext() {
            @Override
            public WebServerConfig serverConfig() {
                throw new AssertionError("Server config is not expected");
            }

            @Override
            public Set<String> sockets() {
                return Set.of();
            }

            @Override
            public boolean socketExists(String socketName) {
                return true;
            }

            @Override
            public ServerFeature.SocketBuilders socket(String socketName) {
                return new ServerFeature.SocketBuilders() {
                    @Override
                    public ListenerConfig listener() {
                        throw new AssertionError("Listener config is not expected");
                    }

                    @Override
                    public HttpRouting.Builder httpRouting() {
                        return applicationRouting;
                    }

                    @Override
                    public ServerFeature.RoutingBuilders routingBuilders() {
                        throw new AssertionError("Other routing builders are not expected");
                    }
                };
            }
        };
    }

    private static MeterRegistryFormatterProvider formatterProvider(AtomicReference<MeterRegistry> registrySeen) {
        return (mediaType, metricsConfig, meterRegistry, scopeTagName, scopeSelection, nameSelection) -> {
            registrySeen.set(meterRegistry);
            return Optional.of(new MeterRegistryFormatter() {
                @Override
                public Optional<Object> format() {
                    return Optional.empty();
                }

                @Override
                public Optional<Object> formatMetadata() {
                    return Optional.empty();
                }
            });
        };
    }

    private static boolean hasMeter(MeterRegistry meterRegistry, String name) {
        return meterRegistry.meters()
                .stream()
                .anyMatch(meter -> meter.id().name().equals(name));
    }
}
