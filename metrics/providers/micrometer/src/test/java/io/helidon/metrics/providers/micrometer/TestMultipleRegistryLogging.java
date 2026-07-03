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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
        Config config = Config.just(ConfigSources.create(Map.of("metrics.tags", "cluster=blue",
                                                                "metrics.app-name", "direct")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, config)
                                                                                .build());
        try {
            MeterRegistry meterRegistry = manager.registry().get(MeterRegistry.class);

            io.micrometer.core.instrument.Counter counter =
                    meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class)
                            .counter(counterName);

            assertThat("System tag from the global registry",
                       counter.getId().getTag("cluster"),
                       equalTo("blue"));
            assertThat("App tag from the global registry",
                       counter.getId().getTag("app"),
                       equalTo("direct"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testDeprecatedGlobalRegistryMethodsDoNotMutateSharedRegistry() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = Services.get(MeterRegistry.class);
        MetricsConfig factoryConfig = metricsFactory.metricsConfig();
        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("stableGlobalCounter"));
        AtomicInteger added = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        MetricsConfig ignoredConfig = MetricsConfig.builder()
                .tags(List.of(metricsFactory.tagCreate("ignored", "tag")))
                .build();

        assertThat("No-arg compatibility method",
                   metricsFactory.globalRegistry(),
                   sameInstance(meterRegistry));
        assertThat("Configured compatibility method",
                   metricsFactory.globalRegistry(ignoredConfig),
                   sameInstance(meterRegistry));
        assertThat("Listener compatibility method",
                   metricsFactory.globalRegistry(meter -> added.incrementAndGet(),
                                                 meter -> removed.incrementAndGet(),
                                                 true),
                   sameInstance(meterRegistry));

        meterRegistry.getOrCreate(metricsFactory.counterBuilder("afterIgnoredGlobalMutation"));
        meterRegistry.remove(counter);

        assertThat("Factory configuration", metricsFactory.metricsConfig(), sameInstance(factoryConfig));
        assertThat("Ignored add listener", added.get(), is(0));
        assertThat("Ignored remove listener", removed.get(), is(0));
    }

    @Test
    void testGlobalRegistryIsEnrolledBeforeMetersProvidersRun() {
        String counterName = "providerCounter";
        MetersProvider metersProvider = metricsFactory -> List.of(metricsFactory.counterBuilder(counterName));
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(),
                                                                                   List.of(metersProvider));
        try {
            MeterRegistry meterRegistry = metricsFactory.globalRegistry();

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
    void testMetersProvidersCloseBeforeRegistries() {
        CloseOrderMetersProvider metersProvider = new CloseOrderMetersProvider();
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(),
                                                                                   List.of(metersProvider));
        MeterRegistry meterRegistry = metricsFactory.globalRegistry();

        metricsFactory.close();

        assertThat("Meters provider observes its meter before registry close",
                   metersProvider.observedActiveMeterOnClose,
                   is(true));
        assertThat("Registry closes after the meters provider", meterRegistry.meters(), hasSize(0));
    }

    @Test
    void testFactoryCloseAttemptsEveryRegistryClose() {
        FailingCloseMeterRegistry firstPublisher = new FailingCloseMeterRegistry("first close failure");
        FailingCloseMeterRegistry secondPublisher = new FailingCloseMeterRegistry("second close failure");
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(), List.of());
        metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                   .addPublisher(new TestPublisher(firstPublisher))
                                                   .warnOnMultipleRegistries(false)
                                                   .build());
        metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                   .addPublisher(new TestPublisher(secondPublisher))
                                                   .warnOnMultipleRegistries(false)
                                                   .build());

        IllegalStateException failure = assertThrows(IllegalStateException.class, metricsFactory::close);

        assertThat("First registry close attempted", firstPublisher.closeAttempts.get(), is(1));
        assertThat("Second registry close attempted", secondPublisher.closeAttempts.get(), is(1));
        assertThat("First close failure reported", failure.getMessage(), is("first close failure"));
        assertThat("Later failure is suppressed", failure.getSuppressed().length, is(1));
        assertThat("Second close failure reported",
                   failure.getSuppressed()[0].getMessage(),
                   is("second close failure"));
        assertThat("Every registry is removed after close attempts", metricsFactory.meterRegistryCount(), is(0));
    }

    @Test
    void testFactoryCloseHandlesRepeatedFailureInstance() {
        IllegalStateException repeatedFailure = new IllegalStateException("repeated close failure");
        FailingCloseMeterRegistry firstPublisher = new FailingCloseMeterRegistry(repeatedFailure);
        FailingCloseMeterRegistry secondPublisher = new FailingCloseMeterRegistry(repeatedFailure);
        FailingCloseMeterRegistry thirdPublisher = new FailingCloseMeterRegistry("third close failure");
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(), List.of());
        metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                   .addPublisher(new TestPublisher(firstPublisher))
                                                   .warnOnMultipleRegistries(false)
                                                   .build());
        metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                   .addPublisher(new TestPublisher(secondPublisher))
                                                   .warnOnMultipleRegistries(false)
                                                   .build());
        metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                   .addPublisher(new TestPublisher(thirdPublisher))
                                                   .warnOnMultipleRegistries(false)
                                                   .build());

        IllegalStateException failure = assertThrows(IllegalStateException.class, metricsFactory::close);

        assertThat("Repeated failure is reported", failure, is(repeatedFailure));
        assertThat("First registry close attempted", firstPublisher.closeAttempts.get(), is(1));
        assertThat("Second registry close attempted", secondPublisher.closeAttempts.get(), is(1));
        assertThat("Later registry close attempted", thirdPublisher.closeAttempts.get(), is(1));
        assertThat("Only the distinct later failure is suppressed", failure.getSuppressed().length, is(1));
        assertThat("Later failure is reported",
                   failure.getSuppressed()[0].getMessage(),
                   is("third close failure"));
        assertThat("Every registry is removed after close attempts", metricsFactory.meterRegistryCount(), is(0));
    }

    @Test
    void testRegistryConstructionFailureClosesPublishers() {
        SimpleMeterRegistry publisherRegistry = new SimpleMeterRegistry();
        Tag failingTag = new Tag() {
            @Override
            public String key() {
                throw new IllegalStateException("Cannot read tag");
            }

            @Override
            public String value() {
                return "value";
            }

            @Override
            public <T> T unwrap(Class<? extends T> type) {
                return type.cast(this);
            }
        };
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .publishers(List.of(new TestPublisher(publisherRegistry)))
                .tags(List.of(failingTag))
                .build();
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(), List.of());

        try {
            assertThrows(IllegalStateException.class, () -> metricsFactory.createMeterRegistry(metricsConfig));
            assertThat("Publisher is closed after registry construction fails", publisherRegistry.isClosed(), is(true));
        } finally {
            metricsFactory.close();
        }
    }

    @Test
    void testPublisherCreationFailureClosesEarlierPublishers() {
        SimpleMeterRegistry publisherRegistry = new SimpleMeterRegistry();
        MicrometerMetricsPublisher failingPublisher = new MicrometerMetricsPublisher() {
            @Override
            public Supplier<io.micrometer.core.instrument.MeterRegistry> registry() {
                return () -> {
                    throw new IllegalStateException("Cannot create publisher registry");
                };
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String name() {
                return "failing";
            }

            @Override
            public String type() {
                return "test";
            }
        };
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .publishers(List.of(new TestPublisher(publisherRegistry), failingPublisher))
                .build();
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(MetricsConfig.create(), List.of());

        try {
            assertThrows(IllegalStateException.class, () -> metricsFactory.createMeterRegistry(metricsConfig));
            assertThat("Earlier publisher is closed after a later publisher cannot be created",
                       publisherRegistry.isClosed(),
                       is(true));
        } finally {
            metricsFactory.close();
        }
    }

    @Test
    void testServiceRegistryShutdownReleasesMetricsFactoryState() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        CompositeMeterRegistry delegate;
        Set<io.micrometer.core.instrument.MeterRegistry> publishers;
        try {
            MeterRegistry meterRegistry = manager.registry().get(MeterRegistry.class);
            delegate = (CompositeMeterRegistry) meterRegistry
                    .unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            publishers = Set.copyOf(delegate.getRegistries());
            assertThat("One publisher registry is registered",
                       publishers,
                       hasSize(1));
        } finally {
            manager.shutdown();
        }

        assertThat("Owned composite is closed after service registry shutdown", delegate.isClosed(), is(true));
        publishers.forEach(publisher -> assertThat("Owned publisher is closed", publisher.isClosed(), is(true)));

        manager = ServiceRegistryManager.create();
        try {
            manager.registry().get(MeterRegistry.class);
        } finally {
            manager.shutdown();
        }

        assertThat("Sequential service registry-owned factories do not warn",
                   testHandler.messages(),
                   hasSize(0));
    }

    @Test
    void testNoArgGlobalRegistryUsesOwningServiceRegistryConfig() {
        Config localConfig = Config.just(ConfigSources.create(Map.of("metrics.app-name", "local-app")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class,
                                                                                                     localConfig)
                                                                                .build());
        try {
            MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);

            manager.registry().get(MeterRegistry.class);

            assertThat("Factory retains its owning service registry configuration",
                       metricsFactory.metricsConfig().appName(),
                       is(Optional.of("local-app")));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testServiceRegistryShutdownPreservesOtherFactoryMeters() {
        ServiceRegistryManager firstManager = ServiceRegistryManager.create();
        ServiceRegistryManager secondManager = ServiceRegistryManager.create();
        try {
            MeterRegistry firstRegistry = firstManager.registry().get(MeterRegistry.class);

            MetricsFactory secondFactory = secondManager.registry().get(MetricsFactory.class);
            MeterRegistry secondRegistry = secondManager.registry().get(MeterRegistry.class);
            assertThat("Factories own distinct Micrometer registries",
                       secondRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class),
                       not(sameInstance(firstRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class))));
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
        firstManager.registry().get(MeterRegistry.class);
        secondManager.registry().get(MeterRegistry.class);

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
        CountDownLatch tagAccessed = new CountDownLatch(1);
        CountDownLatch continueTagAccess = new CountDownLatch(1);
        AtomicBoolean blockTagAccess = new AtomicBoolean();
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .tags(List.of(blockingTag(blockTagAccess, tagAccessed, continueTagAccess)))
                .warnOnMultipleRegistries(false)
                .build();
        MicrometerMetricsFactory metricsFactory = MicrometerMetricsFactory.create(metricsConfig, List.of());
        blockTagAccess.set(true);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<MeterRegistry> registryCreation = executor.submit(() -> metricsFactory.globalRegistry());
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
        } finally {
            continueTagAccess.countDown();
            executor.shutdownNow();
            metricsFactory.close();
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

    private record TestPublisher(SimpleMeterRegistry meterRegistry) implements MicrometerMetricsPublisher {
        @Override
        public Supplier<io.micrometer.core.instrument.MeterRegistry> registry() {
            return () -> meterRegistry;
        }

        @Override
        public boolean enabled() {
            return true;
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

    private static class FailingCloseMeterRegistry extends SimpleMeterRegistry {
        private final AtomicInteger closeAttempts = new AtomicInteger();
        private final IllegalStateException failure;

        private FailingCloseMeterRegistry(String failureMessage) {
            this(new IllegalStateException(failureMessage));
        }

        private FailingCloseMeterRegistry(IllegalStateException failure) {
            this.failure = failure;
        }

        @Override
        public void close() {
            closeAttempts.incrementAndGet();
            super.close();
            throw failure;
        }
    }

    private static class CloseOrderMetersProvider implements MetersProvider, AutoCloseable {
        private static final String COUNTER_NAME = "closeOrderCounter";

        private MeterRegistry meterRegistry;
        private boolean observedActiveMeterOnClose;

        @Override
        public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory metricsFactory) {
            return List.of();
        }

        @Override
        public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return List.of(metricsFactory.counterBuilder(COUNTER_NAME));
        }

        @Override
        public void close() {
            Optional<Counter> counter = meterRegistry.counter(COUNTER_NAME, List.of());
            observedActiveMeterOnClose = counter.isPresent() && !meterRegistry.isDeleted(counter.get());
        }
    }

}
