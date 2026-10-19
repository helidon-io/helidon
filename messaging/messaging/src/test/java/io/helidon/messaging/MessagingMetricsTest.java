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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

class MessagingMetricsTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void recordsCompletedAttemptsUsingOwningClock() {
        // Advance only the selected registry's clock, so elapsed time cannot come from a global clock.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics metrics = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");

            var started = recorder.start();
            environment.clock.advance(15);
            recorder.finish(started, false);
            started = recorder.start();
            environment.clock.advance(25);
            recorder.finish(started, true);

            Timer timer = environment.timer("orders");
            assertThat(timer.count(), is(2L));
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS), is(40D));
            assertThat(environment.counter("orders").count(), is(1L));
            assertThat(metrics.channel("orders"), sameInstance(recorder));
            metrics.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void sharesMetersAcrossServiceRegistriesAndRetiresEachOwner() {
        // Two application registries borrow the same meter registry; a surviving graph must retain the shared series.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics first = environment.observe(environment.registry);
            MessagingMetrics second = environment.observe(environment.registry);
            MessagingMetrics.Recorder retired = first.channel("orders");
            MessagingMetrics.Recorder surviving = second.channel("orders");
            retired.finish(retired.start(), false);
            var late = retired.start();
            first.close();

            retired.finish(late, true);
            surviving.finish(surviving.start(), true);
            assertThat(environment.timer("orders").count(), is(2L));
            assertThat(environment.counter("orders").count(), is(1L));
            first.close();
            assertThat(environment.registry.meters().size(), is(2));
            second.close();
            assertThat(environment.registry.meters(), empty());

            MessagingMetrics replacement = environment.observe(environment.registry);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            retired.finish(late, true);
            surviving.finish(late, true);
            current.finish(current.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void separateMeterRegistriesRemainIndependent() {
        // Equal channel names in different meter registries must not share counts or cleanup ownership.
        try (Environment first = new Environment(MetricsConfig.create());
             Environment second = new Environment(MetricsConfig.create())) {
            MessagingMetrics firstMetrics = first.observe(first.registry);
            MessagingMetrics.Recorder firstRecorder = firstMetrics.channel("orders");
            MessagingMetrics.Recorder secondRecorder = second.observe(second.registry).channel("orders");
            firstRecorder.finish(firstRecorder.start(), true);
            secondRecorder.finish(secondRecorder.start(), false);
            firstMetrics.close();

            assertThat(first.registry.meters(), empty());
            assertThat(second.timer("orders").count(), is(1L));
            assertThat(second.counter("orders").count(), is(0L));
        }
    }

    @Test
    void registrationListenerCannotCreateCompetingOwnership() {
        // A same-thread registration callback cannot acquire the series whose registration is still in progress.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            AtomicBoolean entered = new AtomicBoolean();
            AtomicReference<MessagingMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    MessagingMetrics metrics = environment.observe(environment.registry);
                    nested.set(metrics);
                    MessagingMetrics.Recorder recorder = metrics.channel("orders");
                    recorder.finish(recorder.start(), true);
                }
            });

            MessagingMetrics outer = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = outer.channel("orders");
            nested.get().close();
            recorder.finish(recorder.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
            outer.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void removalListenerCannotAcquireRetiringGeneration() {
        // A reentrant listener must not give a nested graph ownership of meters that the outer close is removing.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics outer = environment.observe(environment.registry);
            outer.channel("orders");
            AtomicBoolean entered = new AtomicBoolean();
            AtomicReference<MessagingMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterRemoved(_ -> {
                if (entered.compareAndSet(false, true)) {
                    MessagingMetrics metrics = environment.observe(environment.registry);
                    nested.set(metrics);
                    metrics.channel("orders");
                }
            });

            outer.close();
            assertThat(environment.registry.meters(), empty());
            MessagingMetrics replacement = environment.observe(environment.registry);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            nested.get().close();
            current.finish(current.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
            replacement.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void registrationListenerCanCloseAnotherChannelOwner() {
        // Closing an unrelated owner from a provider callback must not keep its retired meters registered.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics previous = environment.observe(environment.registry);
            previous.channel("previous");
            AtomicBoolean entered = new AtomicBoolean();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    previous.close();
                }
            });

            MessagingMetrics next = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = next.channel("orders");
            recorder.finish(recorder.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            next.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void disabledMetricsNeedNoRegistryOrClock() {
        // Explicitly disabled observation works even when the selected registry has no metrics service.
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .build());
        try {
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .metrics(m -> m.enabled(false))
                                                                       .buildPrototype(), manager.registry());
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            metrics.close();
            assertThat(metrics.channel("other"), sameInstance(recorder));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryWithoutMetricsServiceUsesNoopRecorder() {
        // Metrics are enabled by default, but an application without a provider must still be able to process messages.
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .build());
        try {
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .metrics(m -> m.enabled(true))
                                                                       .buildPrototype(), manager.registry());
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            metrics.close();
            assertThat(metrics.channel("other"), sameInstance(recorder));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void globalDisableAvoidsClockAndRegistration() {
        // Provider-wide disablement must avoid both registration and timing work in messaging.
        try (Environment environment = new Environment(MetricsConfig.builder().enabled(false).build())) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            MessagingMetrics.Recorder recorder = environment.observe(handler).channel("orders");
            recorder.finish(recorder.start(), true);
            assertThat(environment.registry.meters(), empty());
            assertThat(handler.clockRequests.get(), is(0));
        }
    }

    @Test
    void disabledTimerDoesNotReadClockAndFailureCounterRemainsIndependent() {
        // Disabling duration alone removes clock reads without also disabling the failure counter.
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(DURATION))).enabled(false))
                .build();
        try (Environment environment = new Environment(config)) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            MessagingMetrics.Recorder recorder = environment.observe(handler).channel("orders");
            int clockReads = environment.clock.reads.get();
            recorder.finish(recorder.start(), true);

            assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
            assertThat(environment.counter("orders").count(), is(1L));
            assertThat(handler.clockRequests.get(), is(0));
            assertThat(environment.clock.reads.get(), is(clockReads));
        }
    }

    @Test
    void hiddenWrappedTimerReleasesItsNativeCreationHandle() {
        // The adapter defers enablement to registration and wraps the hidden timer; cleanup still owns its native handle.
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(DURATION))).enabled(false))
                .build();
        try (Environment environment = new Environment(config)) {
            MeterRegistry wrapped = new DelegatingMeterRegistry(environment.registry) {
                @Override
                public boolean isMeterEnabled(String name) {
                    return true;
                }

                @Override
                public boolean isMeterEnabled(String name, Map<String, String> tags) {
                    return true;
                }

                @Override
                @SuppressWarnings("unchecked")
                public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder, Consumer<M> onCreate) {
                    M meter = super.getOrCreate(builder, onCreate);
                    return meter instanceof Timer timer ? (M) new DelegatingTimer(timer) : meter;
                }
            };
            MessagingMetrics metrics = environment.observe(wrapped);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            Timer hidden = environment.registry.getOrCreate(environment.factory.timerBuilder(DURATION)
                    .tags(environment.tags("orders")));
            assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
            assertThat(environment.registry.isDeleted(hidden), is(false));

            recorder.finish(recorder.start(), false);
            metrics.close();

            assertThat(environment.registry.isDeleted(hidden), is(true));
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void disabledFailureCounterPreservesProcessingCount() {
        // A failed attempt still contributes to the timer when only the failure counter is disabled.
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(FAILURES))).enabled(false))
                .build();
        try (Environment environment = new Environment(config)) {
            MessagingMetrics.Recorder recorder = environment.observe(environment.registry).channel("orders");
            recorder.finish(recorder.start(), true);

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));
        }
    }

    @Test
    void tagFiltersApplyBeforeAcquiringMeters() {
        // The provider can exclude one channel; messaging must apply that decision before registering or timing it.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            handler.filteredChannel = "ignored";
            MessagingMetrics metrics = environment.observe(handler);
            MessagingMetrics.Recorder ignored = metrics.channel("ignored");
            ignored.finish(ignored.start(), true);
            assertThat(environment.registry.meters(), empty());
            assertThat(handler.clockRequests.get(), is(0));

            MessagingMetrics.Recorder included = metrics.channel("orders");
            included.finish(included.start(), true);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(1L));
        }
    }

    @Test
    void partialRegistrationFailureRollsBackAndDoesNotPreventLaterAcquisition() {
        // Failure of the second meter registration must release the first and leave later graph construction usable.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            handler.fault = Fault.REGISTER;
            MessagingMetrics first = environment.observe(handler);
            MessagingMetrics.Recorder failed = first.channel("orders");
            failed.finish(failed.start(), true);
            assertThat(environment.registry.meters(), empty());

            handler.fault = Fault.NONE;
            MessagingMetrics.Recorder subsequent = environment.observe(handler).channel("orders");
            first.close();
            subsequent.finish(subsequent.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {DURATION, FAILURES})
    void throwingAddListenerRemovesOnlyAttemptedIdentitiesWithGlobalTags(String failingMeter) {
        // Global tags change the registered ID; rollback must remove this attempt while preserving another channel.
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig config = MetricsConfig.builder()
                .appName("messaging-tests")
                .tags(List.of(factory.tagCreate("region", "test")))
                .build();
        try (Environment environment = new Environment(config, factory)) {
            MessagingMetrics existing = environment.observe(environment.registry);
            existing.channel("payments");
            AtomicBoolean failed = new AtomicBoolean();
            environment.registry.onMeterAdded(meter -> {
                if (meter.id().name().equals(failingMeter) && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("Listener failed after registration");
                }
            });

            MessagingMetrics broken = environment.observe(environment.registry);
            broken.channel("orders");
            assertThat(failingMeter, environment.registry.meters().size(), is(2));
            assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));

            MessagingMetrics replacement = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = replacement.channel("orders");
            recorder.finish(recorder.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            broken.close();
            replacement.close();
            assertThat(environment.registry.meters().size(), is(2));
            existing.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void throwingAddListenerCleanupUsesCustomizerTags() {
        // A customizer removes the channel tag, so rollback must use the returned meter rather than the requested tags.
        HelidonMetricsFactory factory = HelidonMetricsFactory.create(builder -> builder
                .addMeterBuilderCustomizer(meter -> meter.tags(List.of())));
        try (Environment environment = new Environment(MetricsConfig.create(), factory)) {
            environment.registry.onMeterAdded(_ -> {
                throw new IllegalStateException("Listener failed after customized registration");
            });
            environment.observe(environment.registry).channel("orders");
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void recordingFailureDisablesOnlyAffectedRecorder() {
        // One graph's recording failure must not disable another graph that shares its meter registry and channel.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            MessagingMetrics first = environment.observe(handler);
            MessagingMetrics.Recorder broken = first.channel("orders");
            MessagingMetrics.Recorder surviving = environment.observe(handler).channel("orders");
            handler.fault = Fault.RECORD;
            broken.finish(broken.start(), true);
            handler.fault = Fault.NONE;
            broken.finish(broken.start(), true);
            surviving.finish(surviving.start(), false);

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void clockFailureDisablesRecorderAndStillReleasesMeters() {
        // Clock failure stops further observation but must not lose the ownership needed for graph cleanup.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics metrics = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            environment.clock.fail = true;
            recorder.finish(recorder.start(), true);
            environment.clock.fail = false;
            recorder.finish(recorder.start(), true);
            assertThat(environment.timer("orders").count(), is(0L));
            metrics.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void removalFailureStillRetiresLeaseAndAttemptsBothMeters() {
        // A provider that rejects removal retains its meters; messaging must retire its lease and attempt both removals.
        try (Environment environment = new Environment(MetricsConfig.create())) {
            FaultyRegistry handler = new FaultyRegistry(environment.registry);
            MessagingMetrics metrics = environment.observe(handler);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            var started = recorder.start();
            Timer retainedTimer = environment.timer("orders");
            Counter retainedCounter = environment.counter("orders");
            handler.fault = Fault.REMOVE;
            metrics.close();
            assertThat(handler.removals.get(), is(2));
            recorder.finish(started, true);
            assertThat(environment.timer("orders").count(), is(0L));

            handler.fault = Fault.NONE;
            MessagingMetrics replacement = environment.observe(handler);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            current.finish(current.start(), false);
            replacement.close();
            // Failed cleanup released internal ownership; a later graph borrows the pre-existing series.
            assertThat(environment.timer("orders"), sameInstance(retainedTimer));
            assertThat(environment.counter("orders"), sameInstance(retainedCounter));
            assertThat(retainedTimer.count(), is(1L));
            assertThat(retainedCounter.count(), is(0L));
        }
    }

    private enum Fault {
        NONE,
        REGISTER,
        RECORD,
        REMOVE
    }

    private static final class Environment implements AutoCloseable {
        private final TestClock clock = new TestClock();
        private final HelidonMetricsFactory factory;
        private final List<ServiceRegistryManager> managers = new ArrayList<>();
        private final List<MessagingMetrics> observations = new ArrayList<>();
        private final MeterRegistry registry;

        private Environment(MetricsConfig config) {
            this(config, HelidonMetricsFactory.create());
        }

        private Environment(MetricsConfig config, HelidonMetricsFactory factory) {
            this.factory = factory;
            registry = factory.createMeterRegistry(clock, config);
        }

        @Override
        public void close() {
            observations.forEach(MessagingMetrics::close);
            managers.forEach(ServiceRegistryManager::shutdown);
            factory.close();
        }

        private MessagingMetrics observe(MeterRegistry meterRegistry) {
            ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                  .discoverServices(false)
                                                                                  .putContractInstance(MeterRegistry.class,
                                                                                                       meterRegistry)
                                                                                  .build());
            managers.add(manager);
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .metrics(m -> m.enabled(true))
                                                                       .buildPrototype(), manager.registry());
            observations.add(metrics);
            return metrics;
        }

        private List<Tag> tags(String channel) {
            return List.of(factory.tagCreate("channel", channel));
        }

        private Timer timer(String channel) {
            return registry.timer(DURATION, tags(channel)).orElseThrow();
        }

        private Counter counter(String channel) {
            return registry.counter(FAILURES, tags(channel)).orElseThrow();
        }
    }

    private static final class TestClock implements Clock {
        private final AtomicLong now = new AtomicLong();
        private final AtomicInteger reads = new AtomicInteger();
        private boolean fail;

        @Override
        public long wallTime() {
            return TimeUnit.NANOSECONDS.toMillis(now.get());
        }

        @Override
        public long monotonicTime() {
            reads.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("Clock unavailable");
            }
            return now.get();
        }

        private void advance(long nanos) {
            now.addAndGet(nanos);
        }
    }

    private static final class FaultyRegistry extends DelegatingMeterRegistry {
        private final AtomicInteger clockRequests = new AtomicInteger();
        private final AtomicInteger removals = new AtomicInteger();
        private Fault fault = Fault.NONE;
        private String filteredChannel;

        private FaultyRegistry(MeterRegistry delegate) {
            super(delegate);
        }

        @Override
        public Clock clock() {
            clockRequests.incrementAndGet();
            return super.clock();
        }

        @Override
        public boolean isMeterEnabled(String name, Map<String, String> tags) {
            return !tags.get("channel").equals(filteredChannel) && super.isMeterEnabled(name, tags);
        }

        @Override
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
            return getOrCreate(builder, _ -> { });
        }

        @Override
        @SuppressWarnings("unchecked")
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder, Consumer<M> onCreate) {
            if (builder instanceof Counter.Builder && fault == Fault.REGISTER) {
                throw new IllegalStateException("Counter registration unavailable");
            }
            M result = super.getOrCreate(builder, onCreate);
            if (result instanceof Timer timer) {
                return (M) new DelegatingTimer(timer) {
                    @Override
                    public void record(long amount, TimeUnit unit) {
                        if (fault == Fault.RECORD) {
                            throw new IllegalStateException("Recording unavailable");
                        }
                        super.record(amount, unit);
                    }
                };
            }
            return result;
        }

        @Override
        public Optional<Meter> remove(Meter meter) {
            removals.incrementAndGet();
            if (fault == Fault.REMOVE) {
                throw new IllegalStateException("Removal unavailable");
            }
            return super.remove(meter);
        }
    }
}
