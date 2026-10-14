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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.metrics.spi.MetersProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestHelidonPublisherLifecycle {

    @Test
    void factoryKeepsBuiltInMeterSourcesAliveThroughFinalExport() {
        class Source implements MetersProvider, AutoCloseable {
            private boolean closed;

            @Override
            public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory factory) {
                return List.of(factory.gaugeBuilder("source", () -> closed ? -1 : 5));
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var source = new Source();
        var exports = new AtomicInteger();
        var publisher = new TestPublisher(true, (registry, _) -> {
            assertThat("Built-in meters exist before publishing starts",
                       registry.gauge("source", List.of()).orElseThrow().value(), is(5));
            return () -> {
                assertThat("Final export reads the open meter source",
                           registry.gauge("source", List.of()).orElseThrow().value(), is(5));
                exports.incrementAndGet();
            };
        });
        var factory = HelidonMetricsFactory.builder()
                .addMetersProvider(source)
                .metricsConfig(MetricsConfig.builder().addPublisher(publisher))
                .build();
        try {
            factory.globalRegistry();
            factory.close();
            assertThat("Publisher performs its final export", exports.get(), is(1));
            assertThat("Factory then closes the meter source", source.closed, is(true));
        } finally {
            factory.close();
        }
    }

    @Test
    void sessionsBelongToEachRegistryAndCloseBeforeMetersAreRemoved() {
        Map<MeterRegistry, MetricsConfig> started = new ConcurrentHashMap<>();
        List<MeterRegistry> stopped = new ArrayList<>();
        var publisher = new TestPublisher(true, (registry, config) -> {
            started.put(registry, config);
            return () -> {
                assertThat("Final export sees the live counter", registry.counter("requests", List.of()).orElseThrow().count(),
                           is(3L));
                stopped.add(registry);
            };
        });
        var factoryConfig = MetricsConfig.builder().appName("shared").addPublisher(publisher).build();
        var customConfig = MetricsConfig.builder().appName("custom").addPublisher(publisher).build();
        var factory = HelidonMetricsFactory.builder().metricsConfig(factoryConfig).build();
        try {
            MeterRegistry shared = factory.globalRegistry();
            MeterRegistry custom = factory.createMeterRegistry(customConfig);
            assertThat("Shared session receives factory configuration", started.get(shared), sameInstance(factoryConfig));
            assertThat("Custom session receives registry configuration", started.get(custom), sameInstance(customConfig));
            for (MeterRegistry registry : List.of(shared, custom)) {
                Counter counter = registry.getOrCreate(factory.counterBuilder("requests"));
                counter.increment(3);
                registry.onMeterRemoved(_ -> assertThat("Publisher closes before removal callbacks",
                                                       stopped.contains(registry), is(true)));
            }

            custom.close();
            custom.close();
            assertThat("Closing one registry leaves the other session live", stopped, contains(custom));
            assertThat("Custom registry is empty after closing", custom.meters(), empty());
            factory.close();
            factory.close();
            assertThat("Factory closes the remaining session exactly once", stopped, contains(custom, shared));
        } finally {
            factory.close();
        }
    }

    @Test
    void disabledMetricsAndDisabledPublishersDoNotStartSessions() {
        AtomicInteger starts = new AtomicInteger();
        var publisher = new TestPublisher(true, (_, _) -> {
            starts.incrementAndGet();
            return () -> { };
        });
        var factory = HelidonMetricsFactory.create();
        try {
            factory.createMeterRegistry(MetricsConfig.builder().enabled(false).addPublisher(publisher).build());
            factory.createMeterRegistry(MetricsConfig.builder()
                                                .addPublisher(new TestPublisher(false, publisher.startup()))
                                                .addPublisher(new ForeignPublisher(false))
                                                .build());
            factory.createMeterRegistry(MetricsConfig.builder().enabled(false).addPublisher(new ForeignPublisher(true)).build());
            assertThat("Disabled metrics and publishers never start publishing", starts.get(), is(0));
        } finally {
            factory.close();
        }
    }

    @Test
    void unsupportedEnabledPublisherFailsClearly() {
        var factory = HelidonMetricsFactory.create();
        try {
            var failure = assertThrows(IllegalArgumentException.class,
                                       () -> factory.createMeterRegistry(MetricsConfig.builder()
                                                                                 .addPublisher(new ForeignPublisher(true))
                                                                                 .build()));
            assertThat(failure.getMessage(), containsString(ForeignPublisher.class.getName()));
            assertThat(failure.getMessage(), containsString(HelidonMetricsPublisher.class.getName()));
        } finally {
            factory.close();
        }
    }

    @Test
    void startupFailureClosesEarlierSessionsAndPreservesOriginalFailure() {
        var startupFailure = new IllegalStateException("start failed");
        var closeFailure = new IllegalStateException("close failed");
        List<String> stopped = new ArrayList<>();
        var failedRegistry = new AtomicReference<MeterRegistry>();
        var first = new TestPublisher(true, (registry, _) -> {
            failedRegistry.set(registry);
            registry.getOrCreate(registry.metricsFactory().counterBuilder("partial.start"));
            return () -> stopped.add("first");
        });
        var second = new TestPublisher(true, (_, _) -> () -> {
            stopped.add("second");
            throw closeFailure;
        });
        var third = new TestPublisher(true, (_, _) -> {
            throw startupFailure;
        });
        var factory = HelidonMetricsFactory.create();
        try {
            var failure = assertThrows(IllegalStateException.class,
                                       () -> factory.createMeterRegistry(MetricsConfig.builder()
                                                                                 .publishers(List.of(first, second, third))
                                                                                 .build()));
            assertThat("Startup failure is preserved", failure, sameInstance(startupFailure));
            assertThat("Cleanup failures remain visible", List.of(failure.getSuppressed()), contains(closeFailure));
            assertThat("Partial sessions close in reverse startup order", stopped, contains("second", "first"));
            assertThat("Failed registry has no meters", failedRegistry.get().meters(), empty());
            failedRegistry.get().close();
            assertThat("Partial sessions are not closed again", stopped, contains("second", "first"));
        } finally {
            factory.close();
        }
    }

    @Test
    void sessionCloseFailureDoesNotPreventOtherCleanup() {
        var closes = new AtomicInteger();
        var failure = new IllegalStateException("publisher close failed");
        var first = new TestPublisher(true, (_, _) -> closes::incrementAndGet);
        var second = new TestPublisher(true, (_, _) -> () -> {
            throw failure;
        });
        var factory = HelidonMetricsFactory.create();
        try {
            var registry = factory.createMeterRegistry(MetricsConfig.builder().publishers(List.of(first, second)).build());
            var counter = registry.getOrCreate(factory.counterBuilder("live"));
            assertThat(assertThrows(IllegalStateException.class, registry::close), sameInstance(failure));
            assertThat("Other sessions still close", closes.get(), is(1));
            assertThat("Meters are removed despite publisher failure", registry.isDeleted(counter), is(true));
            registry.close();
            assertThat("Repeated close does not repeat session cleanup", closes.get(), is(1));
        } finally {
            factory.close();
        }
    }

    @Test
    void registryCloseDuringStartClosesReturnedSessionWithoutStartingAnother() {
        var closes = new AtomicInteger();
        var starts = new AtomicInteger();
        var first = new TestPublisher(true, (registry, _) -> {
            registry.close();
            return () -> {
                registry.close();
                closes.incrementAndGet();
            };
        });
        var second = new TestPublisher(true, (_, _) -> {
            starts.incrementAndGet();
            return () -> { };
        });
        var factory = HelidonMetricsFactory.create();
        try {
            var registry = factory.createMeterRegistry(MetricsConfig.builder().publishers(List.of(first, second)).build());
            assertThat("Session returned after close is cleaned up", closes.get(), is(1));
            assertThat("No publisher starts after registry closure", starts.get(), is(0));
            assertThrows(IllegalStateException.class, () -> registry.getOrCreate(factory.counterBuilder("closed")));
        } finally {
            factory.close();
        }
    }

    @Test
    void concurrentRegistryCloseWaitsForStartupAndSupportsReentrantFactoryClose() throws Exception {
        var startupEntered = new CountDownLatch(1);
        var finishStartup = new CountDownLatch(1);
        var closeEntered = new CountDownLatch(1);
        var registryRef = new AtomicReference<MeterRegistry>();
        var closes = new AtomicInteger();
        var publisher = new TestPublisher(true, (registry, _) -> {
            registryRef.set(registry);
            startupEntered.countDown();
            await(finishStartup);
            return () -> {
                registry.metricsFactory().close();
                closes.incrementAndGet();
            };
        });
        var config = MetricsConfig.builder().addPublisher(publisher).build();
        var factory = HelidonMetricsFactory.create();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var creating = executor.submit(() -> factory.createMeterRegistry(config));
            assertThat("Publisher startup entered", startupEntered.await(5, TimeUnit.SECONDS), is(true));
            var closing = executor.submit(() -> {
                closeEntered.countDown();
                registryRef.get().close();
            });
            assertThat("Concurrent close entered", closeEntered.await(5, TimeUnit.SECONDS), is(true));
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            finishStartup.countDown();
            creating.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);
            assertThat("Returned session closes exactly once", closes.get(), is(1));
            assertThrows(IllegalStateException.class, factory::meterRegistryBuilder);
        } finally {
            finishStartup.countDown();
            executor.shutdownNow();
            assertThat("Publisher lifecycle workers stop", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
            factory.close();
        }
    }

    @Test
    void concurrentFactoryCloseWaitsForStartupAndSupportsReentrantClose() throws Exception {
        var startupEntered = new CountDownLatch(1);
        var finishStartup = new CountDownLatch(1);
        var closeEntered = new CountDownLatch(1);
        var closes = new AtomicInteger();
        var publisher = new TestPublisher(true, (registry, _) -> {
            startupEntered.countDown();
            await(finishStartup);
            registry.metricsFactory().close();
            return closes::incrementAndGet;
        });
        var config = MetricsConfig.builder().addPublisher(publisher).build();
        var factory = HelidonMetricsFactory.create();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var creating = executor.submit(() -> factory.createMeterRegistry(config));
            assertThat("Publisher startup entered", startupEntered.await(5, TimeUnit.SECONDS), is(true));
            var closing = executor.submit(() -> {
                closeEntered.countDown();
                factory.close();
            });
            assertThat("Concurrent factory close entered", closeEntered.await(5, TimeUnit.SECONDS), is(true));
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            finishStartup.countDown();
            var registry = creating.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);
            assertThat("Factory close stops the returned publisher session", closes.get(), is(1));
            assertThrows(IllegalStateException.class, () -> registry.getOrCreate(factory.counterBuilder("closed")));
        } finally {
            finishStartup.countDown();
            executor.shutdownNow();
            assertThat("Factory lifecycle workers stop", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
            factory.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Publisher startup released", latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Publisher startup interrupted", e);
        }
    }

    private record TestPublisher(boolean enabled,
                                 BiFunction<MeterRegistry, MetricsConfig, HelidonMetricsPublisher.Session> startup)
            implements HelidonMetricsPublisher {

        @Override
        public Session start(MeterRegistry registry, MetricsConfig metricsConfig) {
            return startup.apply(registry, metricsConfig);
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }

    private record ForeignPublisher(boolean enabled) implements MetricsPublisher {

        @Override
        public String name() {
            return "foreign";
        }

        @Override
        public String type() {
            return "foreign";
        }
    }
}
