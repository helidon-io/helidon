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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestHelidonRegistryLifecycle {

    @ParameterizedTest
    @EnumSource(ListenerOperation.class)
    void factoryCloseFromMeterListenerCompletes(ListenerOperation operation) throws Exception {
        assertFactoryCloseFromListenerCompletes(operation, CloseInterleaving.NONE);
    }

    @ParameterizedTest
    @EnumSource(ListenerOperation.class)
    void factoryCloseFromMeterListenerCompletesDuringFactoryClose(ListenerOperation operation) throws Exception {
        assertFactoryCloseFromListenerCompletes(operation, CloseInterleaving.FACTORY_CLOSE);
    }

    @ParameterizedTest
    @EnumSource(ListenerOperation.class)
    void factoryCloseFromMeterListenerCompletesDuringRegistryStartup(ListenerOperation operation) throws Exception {
        assertFactoryCloseFromListenerCompletes(operation, CloseInterleaving.REGISTRY_STARTUP);
    }

    @Test
    void closeCannotRepopulateRegistryFromRemoveListener() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        Counter.Builder counterBuilder = factory.counterBuilder("after.close");
        registry.getOrCreate(factory.counterBuilder("before.close"));
        registry.onMeterRemoved(_ -> assertThrows(IllegalStateException.class,
                                                        () -> registry.getOrCreate(counterBuilder)));

        registry.close();

        assertThat(registry.meters(), empty());
    }

    @Test
    void closeIsolatesRemoveListenerFailures() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        registry.getOrCreate(factory.counterBuilder("before.close"));
        registry.onMeterRemoved(_ -> {
            throw new IllegalStateException("listener failure");
        });

        registry.close();

        assertThat(registry.meters(), empty());
    }

    @Test
    void factoryCloseClearsRegistriesWhenListenerFails() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.globalRegistry();
        registry.getOrCreate(factory.counterBuilder("before.close"));
        registry.onMeterRemoved(_ -> {
            throw new IllegalStateException("listener failure");
        });

        factory.close();

        assertThat("Closed registry is empty", registry.meters(), empty());
        assertThrows(IllegalStateException.class, factory::globalRegistry);
    }

    @Test
    void factoryCloseRejectsRegistryCreationFromRemoveListener() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        registry.getOrCreate(factory.counterBuilder("before.close"));
        registry.onMeterRemoved(_ -> assertThrows(IllegalStateException.class,
                                                        () -> factory.createMeterRegistry(MetricsConfig.create())));

        factory.close();
        assertThrows(IllegalStateException.class, () -> factory.createMeterRegistry(MetricsConfig.create()));
    }

    @Test
    @SuppressWarnings("removal")
    void globalRegistryKeepsFactoryConfiguration() {
        MetricsConfig config = MetricsConfig.builder().appName("factory-app").build();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder().metricsConfig(config).build();
        try {
            MeterRegistry registry = factory.globalRegistry();
            Counter counter = registry.getOrCreate(factory.counterBuilder("global.counter"));
            counter.increment();
            AtomicInteger ignoredCallbacks = new AtomicInteger();

            assertThat("Compatibility configuration does not replace registry",
                       factory.globalRegistry(MetricsConfig.builder().enabled(false).build()),
                       sameInstance(registry));
            assertThat("Compatibility listeners do not replace registry",
                       factory.globalRegistry(_ -> ignoredCallbacks.incrementAndGet(),
                                              _ -> ignoredCallbacks.incrementAndGet(),
                                              true),
                       sameInstance(registry));
            registry.getOrCreate(factory.counterBuilder("later.counter"));

            assertThat("Factory configuration remains unchanged", factory.metricsConfig(), sameInstance(config));
            assertThat("Existing meter remains live", counter.count(), is(1L));
            assertThat("Existing meter is not deleted", registry.isDeleted(counter), is(false));
            assertThat("Compatibility callbacks are ignored", ignoredCallbacks.get(), is(0));
        } finally {
            factory.close();
        }
    }

    @Test
    void factoryCloseOwnsBuilderCreatedRegistries() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry.Builder<?, ?> builder = factory.meterRegistryBuilder();
        MeterRegistry registry = builder.build();
        Counter counter = registry.getOrCreate(factory.counterBuilder("builder.counter"));

        factory.close();

        assertThat("Builder-created registry is empty", registry.meters(), empty());
        assertThat("Builder-created meter is deleted", registry.isDeleted(counter), is(true));
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, factory::meterRegistryBuilder);
        assertThrows(IllegalStateException.class, factory::globalRegistry);
        assertThrows(IllegalStateException.class,
                     () -> registry.getOrCreate(factory.counterBuilder("after.close")));
    }

    @Test
    void acceptsAbsentOptionalMeterMetadata() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
            Gauge<Double> gauge = registry.getOrCreate(factory.gaugeBuilder("unitless.gauge", 42L, Long::doubleValue)
                                                              .description(null)
                                                              .baseUnit(null));
            FunctionalCounter functionalCounter = registry.getOrCreate(
                    factory.functionalCounterBuilder("unitless.function", 7L, value -> value)
                            .description(null)
                            .baseUnit(null));
            Counter counter = registry.getOrCreate(factory.counterBuilder("unitless.counter")
                                                          .description(null)
                                                          .baseUnit(null));
            DistributionSummary summary = registry.getOrCreate(
                    factory.distributionSummaryBuilder("unitless.summary", factory.distributionStatisticsConfigBuilder())
                            .description(null)
                            .baseUnit(null));
            Timer timer = registry.getOrCreate(factory.timerBuilder("undescribed.timer").description(null));

            for (Meter meter : List.of(gauge, functionalCounter, counter, summary, timer)) {
                assertThat(meter.id().name() + " has no unit", meter.baseUnit(), is(Optional.empty()));
                assertThat(meter.id().name() + " has no description", meter.description(), is(Optional.empty()));
            }
            assertThat("Unitless gauge reports its value", gauge.value(), is(42D));
            assertThat("Unitless functional counter reports its value", functionalCounter.count(), is(7L));
        } finally {
            factory.close();
        }
    }

    @Test
    void gaugeRejectsNullSamplesAndRecovers() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
            AtomicReference<Long> sample = new AtomicReference<>();
            Gauge<Long> gauge = registry.getOrCreate(factory.gaugeBuilder("non.null.gauge", sample::get));

            assertThrows(NullPointerException.class, gauge::value);
            sample.set(17L);
            assertThat("Gauge samples the newly available value", gauge.value(), is(17L));
            sample.set(null);
            assertThrows(NullPointerException.class, gauge::value);
            sample.set(29L);
            assertThat("Gauge samples recover after a rejected null value", gauge.value(), is(29L));
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsNullApiValues() throws Exception {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        DistributionStatisticsConfig.Builder builder = factory.distributionStatisticsConfigBuilder();
        Timer.Builder timerBuilder = factory.timerBuilder("timer");
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());

        assertThrows(NullPointerException.class,
                     () -> HelidonMetricsFactory.create((HelidonMetricsFactoryConfig) null));
        assertThrows(NullPointerException.class,
                     () -> HelidonMetricsFactory.create(
                             (Consumer<HelidonMetricsFactoryConfig.Builder>) null));
        assertThrows(NullPointerException.class, () -> factory.globalRegistry(null));
        assertThrows(NullPointerException.class, () -> factory.createMeterRegistry((MetricsConfig) null));
        assertThrows(NullPointerException.class,
                     () -> factory.createMeterRegistry(HelidonClock.SYSTEM, (MetricsConfig) null));
        assertThrows(NullPointerException.class,
                     () -> factory.createMeterRegistry(null, MetricsConfig.create()));
        assertThrows(NullPointerException.class,
                     () -> factory.createMeterRegistry(MetricsConfig.create(), null, _ -> { }));
        assertThrows(NullPointerException.class,
                     () -> factory.createMeterRegistry(MetricsConfig.create(), _ -> { }, null));
        assertThrows(NullPointerException.class, () -> factory.counterBuilder(null));
        assertThrows(NullPointerException.class,
                     () -> factory.functionalCounterBuilder(null, new Object(), _ -> 1L));
        assertThrows(NullPointerException.class,
                     () -> factory.functionalCounterBuilder("functional.counter", null, _ -> 1L));
        assertThrows(NullPointerException.class,
                     () -> factory.functionalCounterBuilder("functional.counter", new Object(), null));
        assertThrows(NullPointerException.class, () -> factory.distributionSummaryBuilder(null, builder));
        assertThrows(NullPointerException.class, () -> factory.distributionSummaryBuilder("summary", null));
        assertThrows(NullPointerException.class, () -> factory.gaugeBuilder(null, () -> 1L));
        assertThrows(NullPointerException.class, () -> factory.gaugeBuilder("gauge", (Supplier<Long>) null));
        assertThrows(NullPointerException.class,
                     () -> factory.gaugeBuilder(null, new Object(), _ -> 1D));
        assertThrows(NullPointerException.class,
                     () -> factory.gaugeBuilder("gauge", null, _ -> 1D));
        assertThrows(NullPointerException.class,
                     () -> factory.gaugeBuilder("gauge", new Object(), null));
        assertThrows(NullPointerException.class, () -> factory.timerBuilder(null));
        assertThrows(NullPointerException.class, () -> factory.timerStart((MeterRegistry) null));
        assertThrows(NullPointerException.class, () -> factory.timerStart((Clock) null));
        assertThrows(NullPointerException.class, () -> factory.tagCreate(null, "value"));
        assertThrows(NullPointerException.class, () -> factory.tagCreate("key", null));
        assertThrows(NullPointerException.class, () -> builder.minimumExpectedValue(null));
        assertThrows(NullPointerException.class, () -> builder.maximumExpectedValue(null));
        assertThrows(NullPointerException.class, () -> builder.percentiles((double[]) null));
        assertThrows(NullPointerException.class, () -> builder.percentiles((Iterable<Double>) null));
        assertThrows(NullPointerException.class, () -> builder.percentiles(Arrays.asList(0.5, null)));
        assertThrows(NullPointerException.class, () -> builder.buckets((double[]) null));
        assertThrows(NullPointerException.class, () -> builder.buckets((Iterable<Double>) null));
        assertThrows(NullPointerException.class, () -> builder.buckets(Arrays.asList(1D, null)));
        DistributionSummary.Builder summaryBuilder = factory.distributionSummaryBuilder("summary", builder);
        assertThrows(NullPointerException.class, () -> summaryBuilder.distributionStatisticsConfig(null));
        assertThrows(NullPointerException.class, () -> timerBuilder.baseUnit((String) null));
        assertThrows(NullPointerException.class, () -> timerBuilder.baseUnit((TimeUnit) null));
        assertThrows(NullPointerException.class, () -> timerBuilder.percentiles((double[]) null));
        assertThrows(NullPointerException.class, () -> timerBuilder.buckets((Duration[]) null));
        assertThrows(NullPointerException.class, () -> timerBuilder.buckets(Duration.ofMillis(1), null));
        assertThrows(NullPointerException.class, () -> timerBuilder.minimumExpectedValue(null));
        assertThrows(NullPointerException.class, () -> timerBuilder.maximumExpectedValue(null));
        assertThrows(NullPointerException.class,
                     () -> registry.meters((Predicate<Meter>) null));
        assertThrows(NullPointerException.class,
                     () -> registry.meters((Iterable<String>) null));
        assertThrows(NullPointerException.class, () -> factory.noOpMeter(null));
        assertThrows(NullPointerException.class,
                     () -> registry.isMeterEnabled(null, Map.of(), Optional.empty()));
        assertThrows(NullPointerException.class,
                     () -> registry.isMeterEnabled("name", null, Optional.empty()));
        assertThrows(NullPointerException.class,
                     () -> registry.isMeterEnabled("name", Map.of(), null));
        assertThrows(NullPointerException.class, () -> registry.meter(null, "name", List.of()));
        assertThrows(NullPointerException.class, () -> registry.meter(Counter.class, null, List.of()));
        assertThrows(NullPointerException.class, () -> registry.meter(Counter.class, "name", null));
        assertThrows(NullPointerException.class, () -> registry.remove((Meter) null));
        assertThrows(NullPointerException.class, () -> registry.remove((Meter.Id) null));
        assertThrows(NullPointerException.class, () -> registry.remove((String) null, List.of()));
        assertThrows(NullPointerException.class, () -> registry.remove("name", null));
        assertThrows(NullPointerException.class, () -> registry.isDeleted(null));
        FunctionalCounter functionalCounter = registry.getOrCreate(
                factory.functionalCounterBuilder("null.functional.return", new Object(), _ -> null));
        Timer timer = registry.getOrCreate(factory.timerBuilder("null.timer.return"));

        assertThrows(NullPointerException.class, functionalCounter::count);
        assertThat(timer.record((Supplier<Object>) () -> null), nullValue());
        assertThat(timer.record((Callable<Object>) () -> null), nullValue());
        assertThrows(NullPointerException.class, () -> factory.timerStart().stop(null));
    }

    @Test
    void disabledMetersAreCachedWithoutRegistryNotifications() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.builder().enabled(false).build());
            AtomicInteger adds = new AtomicInteger();
            registry.onMeterAdded(_ -> adds.incrementAndGet());
            Counter red = registry.getOrCreate(factory.counterBuilder("disabled.cached")
                                                      .addTag(factory.tagCreate("color", "red"))
                                                      .description("Red counter")
                                                      .baseUnit(Meter.BaseUnits.BYTES));
            Counter blue = registry.getOrCreate(factory.counterBuilder("disabled.cached")
                                                       .addTag(factory.tagCreate("color", "blue"))
                                                       .description("Blue counter")
                                                       .baseUnit(Meter.BaseUnits.BITS));
            red.increment(7);
            blue.increment(11);

            assertAll(() -> assertThat("Different tagged IDs have distinct no-op meters", blue, not(sameInstance(red))),
                      () -> assertThat("Red identity retains its tags", red.id().tagsMap(), is(Map.of("color", "red"))),
                      () -> assertThat("Blue identity retains its tags", blue.id().tagsMap(),
                                       is(Map.of("color", "blue"))),
                      () -> assertThat("Disabled meters retain their name", red.id().name(), is("disabled.cached")),
                      () -> assertThat("Red description", red.description(), is(Optional.of("Red counter"))),
                      () -> assertThat("Blue description", blue.description(), is(Optional.of("Blue counter"))),
                      () -> assertThat("Red base unit", red.baseUnit(), is(Optional.of(Meter.BaseUnits.BYTES))),
                      () -> assertThat("Blue base unit", blue.baseUnit(), is(Optional.of(Meter.BaseUnits.BITS))),
                      () -> assertThat("Repeated red ID reuses its no-op meter", registry.getOrCreate(
                              factory.counterBuilder("disabled.cached").addTag(factory.tagCreate("color", "red"))),
                                       sameInstance(red)),
                      () -> assertThat("Repeated blue ID reuses its no-op meter", registry.getOrCreate(
                              factory.counterBuilder("disabled.cached").addTag(factory.tagCreate("color", "blue"))),
                                       sameInstance(blue)),
                      () -> assertThat("Red meter remains disabled", red.count(), is(0L)),
                      () -> assertThat("Blue meter remains disabled", blue.count(), is(0L)),
                      () -> assertThat("Disabled meters remain absent from enumeration", registry.meters(), empty()),
                      () -> assertThat("Disabled meters do not notify add listeners", adds.get(), is(0)));
        } finally {
            factory.close();
        }
    }

    @Test
    void disabledMetersPreserveOriginCustomization() {
        String origin = TestHelidonRegistryLifecycle.class.getName();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(MetricsConfig.builder().enabled(false))
                .addMeterBuilderCustomizer(builder -> builder.origin().ifPresent(source -> {
                    builder.addTag(new HelidonTag("source", source));
                    builder.description("Created by " + source);
                    builder.baseUnit(Meter.BaseUnits.BYTES);
                }))
                .build();
        try {
            MeterRegistry registry = factory.globalRegistry();
            Counter counter = registry.getOrCreate(factory.counterBuilder("disabled.customized").origin(origin));
            counter.increment(7);

            assertAll(() -> assertThat("Origin customizer supplies the disabled meter's tags",
                                       counter.id().tagsMap(), is(Map.of("source", origin))),
                      () -> assertThat("Origin customizer supplies the disabled meter's description",
                                       counter.description(), is(Optional.of("Created by " + origin))),
                      () -> assertThat("Origin customizer supplies the disabled meter's unit",
                                       counter.baseUnit(), is(Optional.of(Meter.BaseUnits.BYTES))),
                      () -> assertThat("Customized disabled ID remains cached",
                                       registry.getOrCreate(factory.counterBuilder("disabled.customized")
                                                                    .origin(origin)),
                                       sameInstance(counter)),
                      () -> assertThat("Customization does not enable recording", counter.count(), is(0L)),
                      () -> assertThat("Customized disabled meter is not enumerated", registry.meters(), empty()));
        } finally {
            factory.close();
        }
    }

    @ParameterizedTest
    @EnumSource(RemovalOperation.class)
    void globallyDisabledGaugeRemovalAllowsNewSupplier(RemovalOperation operation) {
        assertDisabledGaugeRemoval(MetricsConfig.builder().enabled(false).build(), operation);
    }

    @ParameterizedTest
    @EnumSource(RemovalOperation.class)
    void namePatternDisabledGaugeRemovalAllowsNewSupplier(RemovalOperation operation) {
        assertDisabledGaugeRemoval(MetricsConfig.builder()
                                           .addMeter(meter -> meter.namePattern(Pattern.compile("disabled\\..*"))
                                                   .enabled(false))
                                           .build(), operation);
    }

    @Test
    void disabledMeterIsDeletedAfterRegistryClose() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry firstRegistry = factory.createMeterRegistry(MetricsConfig.builder()
                                                                        .enabled(false)
                                                                        .build());
        Counter counter = firstRegistry.getOrCreate(factory.counterBuilder("disabled.replaced"));

        assertThat(firstRegistry.isDeleted(counter), is(false));

        firstRegistry.close();

        assertThat(firstRegistry.isDeleted(counter), is(true));
    }

    @Test
    void closedRegistryDoesNotReportUnrelatedMeterAsDeleted() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry firstRegistry = factory.createMeterRegistry(MetricsConfig.builder()
                                                                      .enabled(false)
                                                                      .build());
        Counter disabled = firstRegistry.getOrCreate(factory.counterBuilder("disabled.closed"));
        firstRegistry.close();
        MeterRegistry secondRegistry = factory.createMeterRegistry(MetricsConfig.create());
        Counter active = secondRegistry.getOrCreate(factory.counterBuilder("active.after.close"));

        assertThat(firstRegistry.isDeleted(disabled), is(true));
        assertThat(firstRegistry.isDeleted(active), is(false));
    }

    @Test
    void globalRegistryAppliesMetersProvidersWithRegistryConfig() {
        MetricsConfig metricsConfig = MetricsConfig.builder().appName("global-provider-app").build();
        AtomicReference<MetricsConfig> seenConfig = new AtomicReference<>();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .addMetersProvider(metricsFactory -> {
                    seenConfig.set(metricsFactory.metricsConfig());
                    return List.of(metricsFactory.counterBuilder("provided.counter"));
                })
                .build();
        MeterRegistry registry = factory.globalRegistry();

        assertThat(seenConfig.get(), sameInstance(metricsConfig));
        assertThat(registry.meter(Counter.class, "provided.counter", List.of()).isPresent(), is(true));
    }

    @Test
    void localRegistryCreationDoesNotApplyMetersProviders() {
        AtomicInteger providerInvocations = new AtomicInteger();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .addMetersProvider(metricsFactory -> {
                    providerInvocations.incrementAndGet();
                    return List.of(metricsFactory.counterBuilder("provided.counter"));
                })
                .build();

        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());

        assertThat(providerInvocations.get(), is(0));
        assertThat(registry.meter(Counter.class, "provided.counter", List.of()).isPresent(), is(false));
    }

    @Test
    void concurrentGetOrCreateCreatesSingleCounter() throws Exception {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());

        assertConcurrentGetOrCreateSingleMeter(registry,
                                               () -> registry.getOrCreate(factory.counterBuilder("concurrent.counter")));
    }

    @Test
    void concurrentGetOrCreateCreatesSingleTimer() throws Exception {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());

        assertConcurrentGetOrCreateSingleMeter(registry,
                                               () -> registry.getOrCreate(factory.timerBuilder("concurrent.timer")));
    }

    @Test
    void lookupAndRemoveUseMeterIndex() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        Counter counter = registry.getOrCreate(factory.counterBuilder("indexed")
                                                       .addTag(new HelidonTag("k", "v")));

        assertThat(registry.meter(Counter.class, "indexed", List.of(new HelidonTag("k", "v")))
                           .orElseThrow(), sameInstance(counter));
        assertThat(registry.remove("indexed", List.of(new HelidonTag("k", "v"))).orElseThrow(), is(counter));
        assertThat(registry.meter(Counter.class, "indexed", List.of(new HelidonTag("k", "v"))),
                   is(Optional.empty()));
    }

    @Test
    void rejectsNullScopeOnRemove() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());

        assertThrows(NullPointerException.class,
                     () -> registry.remove(new HelidonMeterId("counter", List.of()), null));
        assertThrows(NullPointerException.class,
                     () -> registry.remove("counter", List.of(), null));
    }

    private static void assertDisabledGaugeRemoval(MetricsConfig metricsConfig, RemovalOperation operation) {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
            MeterRegistry unrelatedRegistry = factory.createMeterRegistry(metricsConfig);
            AtomicInteger adds = new AtomicInteger();
            AtomicInteger removes = new AtomicInteger();
            registry.onMeterAdded(_ -> adds.incrementAndGet());
            registry.onMeterRemoved(_ -> removes.incrementAndGet());
            Gauge.Builder<Integer> originalBuilder = factory.gaugeBuilder("disabled.removable", () -> 17)
                    .addTag(factory.tagCreate("color", "red"));
            Gauge.Builder<Integer> replacementBuilder = factory.gaugeBuilder("disabled.removable", () -> 29)
                    .addTag(factory.tagCreate("color", "red"));
            Gauge.Builder<Integer> otherTagsBuilder = factory.gaugeBuilder("disabled.removable", () -> 43)
                    .addTag(factory.tagCreate("color", "blue"));
            Gauge<Integer> original = registry.getOrCreate(originalBuilder);
            Gauge<Integer> otherTags = registry.getOrCreate(otherTagsBuilder);
            Gauge<Integer> unrelated = unrelatedRegistry.getOrCreate(originalBuilder);

            assertAll("Before disabled gauge removal",
                      () -> assertThat("Same ID retains the original gauge", registry.getOrCreate(replacementBuilder),
                                       sameInstance(original)),
                      () -> assertThat("Cached gauge retains the original supplier", original.value(), is(17)),
                      () -> assertThat("Original gauge is live", registry.isDeleted(original), is(false)),
                      () -> assertThat("Disabled gauges are not enumerated", registry.meters(), empty()),
                      () -> assertThat("Disabled gauges do not notify add listeners", adds.get(), is(0)));

            assertThat("Removal returns the original gauge", operation.remove(registry, original).orElse(null),
                       sameInstance(original));
            assertThat("Repeated removal is empty", operation.remove(registry, original), is(Optional.empty()));
            Gauge<Integer> replacement = registry.getOrCreate(replacementBuilder);

            assertAll("After disabled gauge removal",
                      () -> assertThat("Removed gauge is deleted", registry.isDeleted(original), is(true)),
                      () -> assertThat("Same ID creates a new gauge", replacement, not(sameInstance(original))),
                      () -> assertThat("Replacement uses the new supplier", replacement.value(), is(29)),
                      () -> assertThat("Replacement is live", registry.isDeleted(replacement), is(false)),
                      () -> assertThat("Replacement is cached", registry.getOrCreate(originalBuilder),
                                       sameInstance(replacement)),
                      () -> assertThat("Other tags retain their cached gauge", registry.getOrCreate(otherTagsBuilder),
                                       sameInstance(otherTags)),
                      () -> assertThat("Other tags retain their supplier", otherTags.value(), is(43)),
                      () -> assertThat("Other tags remain live", registry.isDeleted(otherTags), is(false)),
                      () -> assertThat("Unrelated same-ID gauge is not deleted", registry.isDeleted(unrelated), is(false)),
                      () -> assertThat("Unrelated registry retains its gauge",
                                       unrelatedRegistry.getOrCreate(originalBuilder), sameInstance(unrelated)),
                      () -> assertThat("Disabled gauges remain absent from enumeration", registry.meters(), empty()),
                      () -> assertThat("Replacement does not notify add listeners", adds.get(), is(0)),
                      () -> assertThat("Disabled removal does not notify remove listeners", removes.get(), is(0)));

            registry.close();

            assertAll("After registry close",
                      () -> assertThat("Removed gauge remains deleted", registry.isDeleted(original), is(true)),
                      () -> assertThat("Replacement is deleted", registry.isDeleted(replacement), is(true)),
                      () -> assertThat("Other tagged gauge is deleted", registry.isDeleted(otherTags), is(true)),
                      () -> assertThat("Unrelated same-ID gauge is not deleted", registry.isDeleted(unrelated), is(false)),
                      () -> assertThat("Unrelated gauge remains live", unrelatedRegistry.isDeleted(unrelated), is(false)),
                      () -> assertThat("Unrelated gauge retains the original supplier", unrelated.value(), is(17)),
                      () -> assertThat("Closing disabled gauges does not notify remove listeners", removes.get(), is(0)));
        } finally {
            factory.close();
        }
    }

    private static void assertFactoryCloseFromListenerCompletes(ListenerOperation operation,
                                                               CloseInterleaving interleaving) throws Exception {
        var factory = HelidonMetricsFactory.builder()
                .metricsConfig(MetricsConfig.builder().publishers(List.of()))
                .build();
        MeterRegistry registry = factory.createMeterRegistry(factory.metricsConfig());
        Counter.Builder builder = factory.counterBuilder("listener.close");
        if (operation == ListenerOperation.REMOVED) {
            registry.getOrCreate(builder);
        }
        Counter.Builder existingBuilder = factory.counterBuilder("startup.existing");
        if (interleaving == CloseInterleaving.REGISTRY_STARTUP) {
            registry.getOrCreate(existingBuilder);
        }
        var callbackEntered = new CountDownLatch(1);
        var allowCallbackClose = new CountDownLatch(interleaving == CloseInterleaving.NONE ? 0 : 1);
        var callbackReturns = new AtomicInteger();
        var callbackMeter = new AtomicReference<Meter>();
        var failure = new AtomicReference<Throwable>();
        Consumer<Meter> listener = meter -> {
            if (!meter.id().name().equals(builder.name())) {
                return;
            }
            callbackMeter.set(meter);
            callbackEntered.countDown();
            try {
                assertThat("Listener close is released", allowCallbackClose.await(15, TimeUnit.SECONDS), is(true));
                factory.close();
                callbackReturns.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            } catch (Throwable e) {
                // Removal listeners isolate failures, so report callback failures explicitly to the test thread.
                failure.compareAndSet(null, e);
            }
        };
        switch (operation) {
            case ADDED -> registry.onMeterAdded(listener);
            case REMOVED -> registry.onMeterRemoved(listener);
        }

        // The old lock cycle is uninterruptible; daemon workers keep a failing test from trapping the test JVM.
        Thread listenerWorker = lifecycleWorker("meter-" + operation, failure, () -> {
            switch (operation) {
                case ADDED -> registry.getOrCreate(builder);
                case REMOVED -> assertThat("Explicit remove finds its meter",
                                           registry.remove(builder.name(), List.of()).isPresent(), is(true));
            }
        });
        Thread closer = lifecycleWorker("factory-close", failure, factory::close);
        Thread startup = lifecycleWorker("registry-startup", failure,
                                         () -> factory.createMeterRegistry(MetricsConfig.builder()
                                                                                  .addPublisher(new RegistryAccessPublisher(
                                                                                          registry, existingBuilder))
                                                                                  .build()));
        List<Thread> workers = List.of(listenerWorker, closer, startup);
        listenerWorker.start();
        try {
            boolean listenerEntered = callbackEntered.await(5, TimeUnit.SECONDS);
            assertThat("Meter listener entered\n" + stacks(workers), listenerEntered, is(true));
            if (interleaving == CloseInterleaving.REGISTRY_STARTUP) {
                startup.start();
                boolean startupBlocked = awaitWaitingIn(startup,
                                                         HelidonMeterRegistry.class, "getOrCreate",
                                                         ReentrantReadWriteLock.ReadLock.class, "lock");
                assertThat("Registry startup waits for the listener's write lock\n" + stacks(workers),
                           startupBlocked, is(true));
            }
            if (interleaving != CloseInterleaving.NONE) {
                closer.start();
                boolean closeBlocked = interleaving == CloseInterleaving.FACTORY_CLOSE
                        ? awaitWaitingIn(closer, HelidonMeterRegistry.class, "close",
                                         ReentrantReadWriteLock.WriteLock.class, "lock")
                        : awaitWaitingIn(closer, HelidonMetricsFactory.class, "close",
                                         AbstractQueuedSynchronizer.ConditionObject.class,
                                         "awaitUninterruptibly");
                assertThat("Factory close reaches the coordinated wait\n" + stacks(workers), closeBlocked, is(true));
            }
        } finally {
            allowCallbackClose.countDown();
            for (Thread worker : workers) {
                if (worker.isAlive()) {
                    worker.join(Duration.ofSeconds(3));
                }
            }
        }

        String evidence = stacks(workers);
        assertAll("Factory close from " + operation + " with " + interleaving + "\n" + evidence,
                  () -> assertThat("Worker and callback failure", failure.get(), nullValue()),
                  () -> assertThat("Listener worker stopped", listenerWorker.isAlive(), is(false)),
                  () -> assertThat("Factory close worker stopped", closer.isAlive(), is(false)),
                  () -> assertThat("Registry startup worker stopped", startup.isAlive(), is(false)),
                  () -> assertThat("Listener close returned exactly once", callbackReturns.get(), is(1)));
        assertAll("Factory cleanup completed",
                  () -> assertThat("Closed registry is empty", registry.meters(), empty()),
                  () -> assertThat("Callback meter is deleted", registry.isDeleted(callbackMeter.get()), is(true)),
                  () -> assertThrows(IllegalStateException.class, factory::globalRegistry),
                  () -> assertThrows(IllegalStateException.class, factory::meterRegistryBuilder));
        factory.close();
    }

    private static Thread lifecycleWorker(String name, AtomicReference<Throwable> failure, Runnable action) {
        return Thread.ofPlatform().daemon().name(name).unstarted(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });
    }

    private static boolean awaitWaitingIn(Thread thread, Class<?> owner, String method,
                                          Class<?> waitOwner, String waitMethod) throws InterruptedException {
        long started = System.nanoTime();
        while (thread.isAlive() && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5)) {
            StackTraceElement[] frames = thread.getStackTrace();
            if (thread.getState() == Thread.State.WAITING
                    && Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals(owner.getName())
                            && frame.getMethodName().equals(method))
                    && Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals(waitOwner.getName())
                            && frame.getMethodName().equals(waitMethod))) {
                return true;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException("Interrupted while observing " + thread.getName());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        return false;
    }

    private static String stacks(List<Thread> threads) {
        var result = new StringBuilder();
        for (Thread thread : threads) {
            result.append(thread.getName()).append(" state=").append(thread.getState()).append('\n');
            for (StackTraceElement frame : thread.getStackTrace()) {
                result.append("  at ").append(frame).append('\n');
            }
        }
        return result.toString();
    }

    private static <M extends Meter> void assertConcurrentGetOrCreateSingleMeter(MeterRegistry registry,
                                                                                 Supplier<M> meterSupplier) throws Exception {
        int threadCount = 8;
        AtomicInteger adds = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<M>> results = new ArrayList<>();
        registry.onMeterAdded(_ -> adds.incrementAndGet());

        try {
            for (int i = 0; i < threadCount; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return meterSupplier.get();
                }));
            }

            M first = results.get(0).get(10, TimeUnit.SECONDS);
            for (Future<M> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS), sameInstance(first));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(registry.meters().size(), is(1));
        assertThat(adds.get(), is(1));
    }

    private enum RemovalOperation {
        METER,
        ID,
        NAME_AND_TAGS;

        private Optional<Meter> remove(MeterRegistry registry, Meter meter) {
            return switch (this) {
                case METER -> registry.remove(meter);
                case ID -> registry.remove(meter.id());
                case NAME_AND_TAGS -> registry.remove(meter.id().name(), meter.id().tags());
            };
        }
    }

    private enum ListenerOperation {
        ADDED,
        REMOVED
    }

    private enum CloseInterleaving {
        NONE,
        FACTORY_CLOSE,
        REGISTRY_STARTUP
    }

    private record RegistryAccessPublisher(MeterRegistry registry, Counter.Builder counterBuilder)
            implements HelidonMetricsPublisher {

        @Override
        public Session start(MeterRegistry registry, MetricsConfig metricsConfig) {
            this.registry.getOrCreate(counterBuilder);
            return () -> { };
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String name() {
            return "registry-access";
        }

        @Override
        public String type() {
            return "test";
        }
    }
}
