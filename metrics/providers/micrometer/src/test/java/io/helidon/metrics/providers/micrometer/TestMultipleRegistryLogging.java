/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class TestMultipleRegistryLogging {

    private static final Logger mmeterRegisteryLogger = Logger.getLogger(MMeterRegistry.class.getName());
    private static TestHandler testHandler;

    @BeforeAll
    static void beforeAll() {
        testHandler = TestHandler.create();
        mmeterRegisteryLogger.addHandler(testHandler);
    }

    @AfterAll
    static void afterAll() {
        mmeterRegisteryLogger.removeHandler(testHandler);
    }

    @BeforeEach
    void setUp() {
        MicrometerMetricsTestsJunitExtension.clear();
    }

    @AfterEach
    void tearDown() {
        testHandler.clear();
    }

    @Test
    void testSingleRegistry() {
        Services.get(MetricsFactory.class).createMeterRegistry(MetricsConfig.create());
        assertThat("Single meter registry", testHandler.messages(), hasSize(0));
    }

    @Test
    void testTwoRegistries() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.createMeterRegistry(MetricsConfig.create());
        metricsFactory.createMeterRegistry(MetricsConfig.create());

        assertThat("Two meter registries", testHandler.messages(),
                   hasItem(allOf(
                           containsString("Unexpected duplicate"),
                           containsString("Original instantiation"),
                           containsString("Additional instantiation"))));
    }

    @Test
    void testThreeRegistries() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.createMeterRegistry(MetricsConfig.create());
        metricsFactory.createMeterRegistry(MetricsConfig.create());
        metricsFactory.createMeterRegistry(MetricsConfig.create());

        assertThat("Three meter registries",
                   testHandler.messages(),
                   allOf(
                           hasItem(allOf(
                                   containsString("Unexpected duplicate"),
                                   containsString("Original instantiation"),
                                   containsString("Additional instantiation"))),
                           hasItem(
                                   containsString("Unexpected additional instantiation"))));
    }

    @Test
    void testTwoRegistriesWithWarningDisabled() {
        MetricsConfig configWithWarningsSuppressed = MetricsConfig.builder().warnOnMultipleRegistries(false).build();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.createMeterRegistry(configWithWarningsSuppressed);
        metricsFactory.createMeterRegistry(configWithWarningsSuppressed);

        assertThat("Two meter registrations with warnings suppressed", testHandler.messages, hasSize(0));

    }

    @Test
    void testProgrammaticRegistriesUseOwnSystemTags() {
        String counterName = "firstRegistryCounter";
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MetricsConfig firstConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("registry", "first")))
                .warnOnMultipleRegistries(false)
                .build();
        MetricsConfig secondConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("registry", "second")))
                .warnOnMultipleRegistries(false)
                .build();

        MeterRegistry firstRegistry = metricsFactory.createMeterRegistry(firstConfig);
        metricsFactory.createMeterRegistry(secondConfig);

        Counter counter = firstRegistry.getOrCreate(metricsFactory.counterBuilder(counterName));
        counter.increment();
        io.micrometer.core.instrument.Counter micrometerCounter =
                counter.unwrap(io.micrometer.core.instrument.Counter.class);

        assertThat("System tag from the first registry",
                   micrometerCounter.getId().getTag("registry"),
                   equalTo("first"));
    }

    @Test
    void testProgrammaticRegistriesWithSameSystemTagKeyRemainDistinct() {
        String counterName = "sameSystemTagKeyCounter";
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MetricsConfig firstConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("cluster", "one")))
                .warnOnMultipleRegistries(false)
                .build();
        MetricsConfig secondConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("cluster", "two")))
                .warnOnMultipleRegistries(false)
                .build();

        MeterRegistry firstRegistry = metricsFactory.createMeterRegistry(firstConfig);
        MeterRegistry secondRegistry = metricsFactory.createMeterRegistry(secondConfig);

        Counter firstCounter = firstRegistry.getOrCreate(metricsFactory.counterBuilder(counterName));
        firstCounter.increment();
        Counter secondCounter = secondRegistry.getOrCreate(metricsFactory.counterBuilder(counterName));
        secondCounter.increment();

        assertThat("System tag from the first registry",
                   firstCounter.unwrap(io.micrometer.core.instrument.Counter.class).getId().getTag("cluster"),
                   equalTo("one"));
        assertThat("System tag from the second registry",
                   secondCounter.unwrap(io.micrometer.core.instrument.Counter.class).getId().getTag("cluster"),
                   equalTo("two"));
    }

    @Test
    void testDirectMicrometerRegistrationUsesGlobalSystemTags() {
        String counterName = "directMicrometerCounter";
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("cluster", "blue")))
                .appTagName("app")
                .appName("direct")
                .warnOnMultipleRegistries(false)
                .build();

        metricsFactory.globalRegistry(metricsConfig);

        io.micrometer.core.instrument.Counter counter =
                io.micrometer.core.instrument.Metrics.counter(counterName);

        assertThat("System tag from the global registry",
                   counter.getId().getTag("cluster"),
                   equalTo("blue"));
        assertThat("App tag from the global registry",
                   counter.getId().getTag("app"),
                   equalTo("direct"));
    }

    @Test
    void testGlobalRegistryIsEnrolledBeforeMetersProvidersRun() {
        String counterName = "providerCounter";
        MetersProvider metersProvider = metricsFactory -> List.of(metricsFactory.counterBuilder(counterName));
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(),
                                                                                   List.of(metersProvider),
                                                                                   ignored -> { });
        try {
            MeterRegistry meterRegistry = metricsFactory.globalRegistry(MetricsConfig.create());

            assertThat("Provider-created meter is recorded by the global registry",
                       meterRegistry.meter(Counter.class, counterName, List.of()).isPresent(),
                       is(true));
            assertThat("Provider-created meter uses the normal Micrometer callback path",
                       testHandler.messages(),
                       not(hasItem(containsString("Unexpected discovery"))));
        } finally {
            metricsFactory.close();
        }
    }

    @Test
    void testServiceRegistryShutdownReleasesMetricsFactoryState() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        try {
            manager.registry()
                    .get(MetricsFactory.class)
                    .globalRegistry(MetricsConfig.create());
            assertThat("One publisher registry is registered",
                       micrometerGlobalRegistry().getRegistries(),
                       hasSize(1));
        } finally {
            manager.shutdown();
        }

        assertThat("No publisher registries remain after service registry shutdown",
                   micrometerGlobalRegistry().getRegistries(),
                   hasSize(0));

        manager = ServiceRegistryManager.create();
        try {
            manager.registry()
                    .get(MetricsFactory.class)
                    .globalRegistry(MetricsConfig.create());
        } finally {
            manager.shutdown();
        }

        assertThat("Sequential service registry-owned factories do not warn",
                   testHandler.messages(),
                   hasSize(0));
    }

    @Test
    void testServiceRegistryShutdownPreservesOtherFactoryMeters() {
        ServiceRegistryManager firstManager = ServiceRegistryManager.create();
        ServiceRegistryManager secondManager = ServiceRegistryManager.create();
        try {
            firstManager.registry().get(MetricsFactory.class).globalRegistry(MetricsConfig.create());

            MetricsFactory secondFactory = secondManager.registry().get(MetricsFactory.class);
            MeterRegistry secondRegistry = secondFactory.globalRegistry(MetricsConfig.create());
            Counter secondCounter = secondRegistry.getOrCreate(secondFactory.counterBuilder("secondFactoryCounter"));
            secondCounter.increment();

            firstManager.shutdown();

            assertThat("Second factory meter remains registered",
                       secondRegistry.meter(Counter.class, "secondFactoryCounter", List.of()).orElseThrow(),
                       sameInstance(secondCounter));
            assertThat("Second factory meter remains active", secondRegistry.isDeleted(secondCounter), is(false));
            assertThat("Second factory meter remains usable", secondCounter.count(), is(1L));
        } finally {
            firstManager.shutdown();
            secondManager.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void testConcurrentServiceRegistryShutdownCompletes() throws Exception {
        ServiceRegistryManager firstManager = ServiceRegistryManager.create();
        ServiceRegistryManager secondManager = ServiceRegistryManager.create();
        firstManager.registry().get(MetricsFactory.class).globalRegistry(MetricsConfig.create());
        secondManager.registry().get(MetricsFactory.class).globalRegistry(MetricsConfig.create());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<?> firstShutdown = executor.submit(() -> {
                await(start);
                firstManager.shutdown();
            });
            Future<?> secondShutdown = executor.submit(() -> {
                await(start);
                secondManager.shutdown();
            });

            start.countDown();
            firstShutdown.get(5, TimeUnit.SECONDS);
            secondShutdown.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testClosedFactoryRejectsNewRegistries() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);
        MetricsConfig metricsConfig = MetricsConfig.create();
        Clock clock = metricsFactory.clockSystem();
        var preCloseBuilder = metricsFactory.meterRegistryBuilder();

        manager.shutdown();

        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.createMeterRegistry(metricsConfig),
                     "Creating a registry after factory shutdown");
        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.createMeterRegistry(metricsConfig, meter -> { }, meter -> { }),
                     "Creating a registry with listeners after factory shutdown");
        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.createMeterRegistry(clock, metricsConfig),
                     "Creating a registry with a clock after factory shutdown");
        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.createMeterRegistry(clock, metricsConfig, meter -> { }, meter -> { }),
                     "Creating a registry with a clock and listeners after factory shutdown");
        assertThrows(IllegalStateException.class,
                     metricsFactory::meterRegistryBuilder,
                     "Obtaining a registry builder after factory shutdown");
        assertThrows(IllegalStateException.class,
                     preCloseBuilder::build,
                     "Building a registry after factory shutdown");
        assertThrows(IllegalStateException.class,
                     metricsFactory::globalRegistry,
                     "Reopening the global registry after factory shutdown");
        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.globalRegistry(metricsConfig),
                     "Reopening the configured global registry after factory shutdown");
        assertThrows(IllegalStateException.class,
                     () -> metricsFactory.globalRegistry(meter -> { }, meter -> { }, false),
                     "Accessing the listener-enabled global registry after factory shutdown");
    }

    @Test
    @Timeout(10)
    void testReentrantCloseFromOnCloseCompletes() throws Exception {
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(),
                                                                                   List.of(),
                                                                                   MicrometerMetricsFactory::close);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<?> close = executor.submit(metricsFactory::close);
            close.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void testRegistryCreationIsAtomicWithFactoryShutdown() throws Exception {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        MicrometerMetricsFactory metricsFactory =
                (MicrometerMetricsFactory) manager.registry().get(MetricsFactory.class);
        CountDownLatch tagAccessed = new CountDownLatch(1);
        CountDownLatch continueTagAccess = new CountDownLatch(1);
        AtomicBoolean blockTagAccess = new AtomicBoolean();
        Tag blockingTag = blockingTag(blockTagAccess, tagAccessed, continueTagAccess);
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .tags(List.of(blockingTag))
                .warnOnMultipleRegistries(false)
                .build();
        blockTagAccess.set(true);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<MeterRegistry> registryCreation =
                    executor.submit(() -> metricsFactory.createMeterRegistry(metricsConfig));
            assertThat("Registry creation reached the coordinated point",
                       tagAccessed.await(5, TimeUnit.SECONDS),
                       is(true));

            Future<?> factoryClose = executor.submit(metricsFactory::close);
            factoryClose.get(5, TimeUnit.SECONDS);

            continueTagAccess.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> registryCreation.get(5, TimeUnit.SECONDS),
                                                      "Registry creation which loses the shutdown race");
            assertThat("Registry creation failure",
                       failure.getCause(),
                       instanceOf(IllegalStateException.class));

            assertThat("Factory shutdown rejected the concurrently-created registry",
                       metricsFactory.meterRegistryCount(),
                       is(0));
        } finally {
            continueTagAccess.countDown();
            executor.shutdownNow();
            manager.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void testGlobalRegistryCreationCompletesBeforeFactoryShutdownCleanup() throws Exception {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        MicrometerMetricsFactory metricsFactory =
                (MicrometerMetricsFactory) manager.registry().get(MetricsFactory.class);
        CountDownLatch tagAccessed = new CountDownLatch(1);
        CountDownLatch continueTagAccess = new CountDownLatch(1);
        AtomicBoolean blockTagAccess = new AtomicBoolean();
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .tags(List.of(blockingTag(blockTagAccess, tagAccessed, continueTagAccess)))
                .warnOnMultipleRegistries(false)
                .build();
        blockTagAccess.set(true);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<MeterRegistry> registryCreation = executor.submit(() -> metricsFactory.globalRegistry(metricsConfig));
            assertThat("Global registry creation reached the coordinated point",
                       tagAccessed.await(5, TimeUnit.SECONDS),
                       is(true));

            Future<?> factoryClose = executor.submit(metricsFactory::close);
            awaitFactoryClosed(metricsFactory);
            assertThat("Factory cleanup waits for global registry creation", factoryClose.isDone(), is(false));

            continueTagAccess.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> registryCreation.get(5, TimeUnit.SECONDS),
                                                      "Global registry creation which loses the shutdown race");
            assertThat("Global registry creation failure",
                       failure.getCause(),
                       instanceOf(IllegalStateException.class));
            factoryClose.get(5, TimeUnit.SECONDS);

            assertThat("Factory shutdown rejected the concurrently-created global registry",
                       metricsFactory.meterRegistryCount(),
                       is(0));
            assertThat("No publisher registries remain after global registry creation loses the shutdown race",
                       micrometerGlobalRegistry().getRegistries(),
                       hasSize(0));
        } finally {
            continueTagAccess.countDown();
            executor.shutdownNow();
            manager.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void testGlobalRegistryListenerUsesStableRegistryDuringFactoryShutdown() throws Exception {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);
        MeterRegistry globalRegistry = metricsFactory.globalRegistry(MetricsConfig.create());
        globalRegistry.getOrCreate(metricsFactory.counterBuilder("globalListenerCounter"));
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch continueListener = new CountDownLatch(1);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<MeterRegistry> listenerRegistration = executor.submit(() ->
                    metricsFactory.globalRegistry(meter -> {
                        listenerEntered.countDown();
                        await(continueListener);
                    }, meter -> { }, true));
            assertThat("Global registry listener reached the coordinated point",
                       listenerEntered.await(5, TimeUnit.SECONDS),
                       is(true));

            Future<?> factoryClose = executor.submit(metricsFactory::close);
            awaitFactoryClosed(metricsFactory);
            assertThat("Factory cleanup waits for global registry listener", factoryClose.isDone(), is(false));

            continueListener.countDown();
            assertThat("Listener registration returns the stable global registry",
                       listenerRegistration.get(5, TimeUnit.SECONDS),
                       sameInstance(globalRegistry));
            factoryClose.get(5, TimeUnit.SECONDS);
        } finally {
            continueListener.countDown();
            executor.shutdownNow();
            manager.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void testFactoryShutdownDoesNotDeadlockMeterListener() throws Exception {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch continueListener = new CountDownLatch(1);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create(), meter -> {
            listenerEntered.countDown();
            await(continueListener);
            metricsFactory.meterRegistryBuilder();
        }, meter -> { });

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<?> meterCreation = executor.submit(() ->
                    meterRegistry.getOrCreate(metricsFactory.counterBuilder("shutdownListenerCounter")));
            assertThat("Meter listener reached the coordinated point",
                       listenerEntered.await(5, TimeUnit.SECONDS),
                       is(true));

            Future<?> factoryClose = executor.submit(metricsFactory::close);
            awaitFactoryClosed(metricsFactory);
            continueListener.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> meterCreation.get(5, TimeUnit.SECONDS),
                                                      "Meter listener accessing a closed factory");
            assertThat("Meter listener failure",
                       failure.getCause(),
                       instanceOf(IllegalStateException.class));
            factoryClose.get(5, TimeUnit.SECONDS);
        } finally {
            continueListener.countDown();
            executor.shutdownNow();
            manager.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating shutdown", e);
        }
    }

    private static void awaitFactoryClosed(MetricsFactory metricsFactory) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                metricsFactory.meterRegistryBuilder();
            } catch (IllegalStateException e) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Metrics factory did not enter the closed state");
    }

    private static Tag blockingTag(AtomicBoolean blockTagAccess,
                                   CountDownLatch tagAccessed,
                                   CountDownLatch continueTagAccess) {
        return new Tag() {
            @Override
            public String key() {
                if (blockTagAccess.get()) {
                    tagAccessed.countDown();
                    await(continueTagAccess);
                }
                return "lifecycle";
            }

            @Override
            public String value() {
                return "test";
            }

            @Override
            public <T> T unwrap(Class<? extends T> type) {
                return type.cast(this);
            }
        };
    }

    private static CompositeMeterRegistry micrometerGlobalRegistry() {
        return io.micrometer.core.instrument.Metrics.globalRegistry;
    }

    private static class TestHandler extends Handler {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        // For testing.
        static TestHandler create() {
            return new TestHandler();
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }

        List<String> messages() {
            return List.copyOf(messages);
        }

        void clear() {
            messages.clear();
        }
    }

}
