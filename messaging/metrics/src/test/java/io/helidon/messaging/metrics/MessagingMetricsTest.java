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

package io.helidon.messaging.metrics;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingGraph;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsTest {
    private static final MessageBatch<String> BATCH = MessageBatch.create(Message.create("order"));
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @ParameterizedTest
    @ValueSource(longs = {0, -100})
    void recordsCompletedAttemptsUsingOwningClock(long startingNanos) {
        // Advance only the selected registry's clock, so elapsed time cannot come from a global clock.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            environment.clock.advance(startingNanos);
            ChannelMetrics metrics = environment.observe();
            var failure = new IllegalStateException("Delivery failed");
            var dispatch = metrics.observe("orders", batch -> {
                if (batch.get(0).entity().equals("fail")) {
                    environment.clock.advance(25);
                    throw failure;
                }
                environment.clock.advance(15);
            });
            dispatch.accept(BATCH);
            var failedBatch = MessageBatch.create(Message.create("fail"));
            assertThat(assertThrows(IllegalStateException.class, () -> dispatch.accept(failedBatch)), sameInstance(failure));

            Timer timer = environment.timer("orders");
            assertThat(timer.count(), is(2L));
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS), is(40D));
            assertThat(environment.counter("orders").count(), is(1L));
            successfulDispatch(metrics, "orders").run();
            assertThat(timer.count(), is(3L));
            metrics.close();
            assertThat(environment.timer("orders").count(), is(3L));
        }
    }

    @Test
    void sharesCumulativeMetersAcrossGraphCloseAndRecreation() {
        // Closing a graph stops its recording, while other and recreated graphs continue the registry's series.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            ChannelMetrics first = environment.observe();
            ChannelMetrics second = environment.observe();
            Runnable retired = successfulDispatch(first, "orders");
            Runnable surviving = failingDispatch(second, "orders");
            retired.run();
            first.close();

            retired.run();
            surviving.run();
            assertThat(environment.timer("orders").count(), is(2L));
            assertThat(environment.counter("orders").count(), is(1L));
            first.close();
            assertThat(environment.registry.meters().size(), is(2));
            second.close();
            assertThat(environment.timer("orders").count(), is(2L));

            ChannelMetrics replacement = environment.observe();
            Runnable current = successfulDispatch(replacement, "orders");
            retired.run();
            surviving.run();
            current.run();
            assertThat(environment.timer("orders").count(), is(3L));
            assertThat(environment.counter("orders").count(), is(1L));
        }
    }

    @Test
    void separateMeterRegistriesRemainIndependent() {
        // Equal channel names in different meter registries must not share counts.
        try (MetricsTestSupport first = new MetricsTestSupport(MetricsConfig.create());
             MetricsTestSupport second = new MetricsTestSupport(MetricsConfig.create())) {
            ChannelMetrics firstMetrics = first.observe();
            Runnable firstRecorder = failingDispatch(firstMetrics, "orders");
            Runnable secondRecorder = successfulDispatch(second.observe(), "orders");
            firstRecorder.run();
            secondRecorder.run();
            firstMetrics.close();

            assertThat(first.timer("orders").count(), is(1L));
            assertThat(first.counter("orders").count(), is(1L));
            assertThat(second.timer("orders").count(), is(1L));
            assertThat(second.counter("orders").count(), is(0L));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nestedGraphRecordsWithIndependentRegistry(boolean returnToFirstRegistry) {
        // A -> B must record normally; an optional B -> A callback must still skip the recursive registration.
        try (MetricsTestSupport first = new MetricsTestSupport();
             MetricsTestSupport second = new MetricsTestSupport()) {
            AtomicBoolean enteredFirst = new AtomicBoolean();
            AtomicBoolean enteredSecond = new AtomicBoolean();
            AtomicReference<MessagingGraph> nested = new AtomicReference<>();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            first.registry.onMeterAdded(_ -> {
                if (enteredFirst.compareAndSet(false, true)) {
                    nested.set(second.graph(channel));
                }
            });
            second.registry.onMeterAdded(_ -> {
                if (returnToFirstRegistry && enteredSecond.compareAndSet(false, true)) {
                    failingDispatch(first.observe(), "recursive").run();
                }
            });

            try (MessagingGraph outer = first.graph(channel)) {
                // Record after both callbacks return: the nested graph must retain working metrics.
                MessagingGraph inner = nested.get();
                outer.emitter(channel).emit("outer");
                inner.emitter(channel).emit("inner");
                assertThrows(BatchDeliveryException.class, () -> inner.emitter(channel).emit("fail"));
                assertThat(first.timer("orders").count(), is(1L));
                assertThat(first.counter("orders").count(), is(0L));
                assertThat(second.timer("orders").count(), is(2L));
                assertThat(second.counter("orders").count(), is(1L));
                assertThat(first.registry.timer(DURATION, first.tags("recursive")).isEmpty(), is(true));
            } finally {
                if (nested.get() != null) {
                    nested.get().close();
                }
            }
        }
    }

    @Test
    void nestedRegistrationFailurePreservesOuterGuardAndAllowsRetry() {
        // B's failed registration must release B while A remains guarded until its callback returns.
        try (MetricsTestSupport first = new MetricsTestSupport();
             MetricsTestSupport second = new MetricsTestSupport()) {
            AtomicBoolean entered = new AtomicBoolean();
            AtomicBoolean fail = new AtomicBoolean(true);
            second.registry.onMeterAdded(meter -> {
                if (meter.id().name().equals(FAILURES) && fail.compareAndSet(true, false)) {
                    throw new AssertionError("Nested registration failure");
                }
            });
            first.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    assertThrows(AssertionError.class, () -> second.observe().observe("orders", _ -> { }));
                    failingDispatch(second.observe(), "orders").run();
                    failingDispatch(first.observe(), "recursive").run();
                }
            });

            successfulDispatch(first.observe(), "orders").run();
            assertThat(first.timer("orders").count(), is(1L));
            assertThat(second.timer("orders").count(), is(1L));
            assertThat(second.counter("orders").count(), is(1L));
            assertThat(first.registry.timer(DURATION, first.tags("recursive")).isEmpty(), is(true));
        }
    }

    @Test
    void sameThreadRegistrationCallbackDoesNotRecordPartiallyRegisteredMeters() {
        // A same-thread registration callback cannot acquire the series whose registration is still in progress.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            AtomicBoolean entered = new AtomicBoolean();
            AtomicReference<ChannelMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    ChannelMetrics metrics = environment.observe();
                    nested.set(metrics);
                    failingDispatch(metrics, "orders").run();
                }
            });

            ChannelMetrics outer = environment.observe();
            Runnable recorder = successfulDispatch(outer, "orders");
            nested.get().close();
            recorder.run();
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
            outer.close();
            assertThat(environment.timer("orders").count(), is(1L));
        }
    }

    @Test
    void registrationListenerCanCloseAnotherGraph() {
        // Closing another graph in a provider callback must stop its recorder without calling back into the provider.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            ChannelMetrics previous = environment.observe();
            Runnable closed = failingDispatch(previous, "previous");
            AtomicBoolean entered = new AtomicBoolean();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    previous.close();
                }
            });

            ChannelMetrics next = environment.observe();
            successfulDispatch(next, "orders").run();
            closed.run();
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.timer("previous").count(), is(0L));
            assertThat(environment.counter("previous").count(), is(0L));
            next.close();
            assertThat(environment.registry.meters().size(), is(4));
        }
    }

    @Test
    void closeDuringRegistrationDoesNotPublishAnActiveRecorder() {
        // The provider closes this observer before channel registration returns; the returned recorder must remain retired.
        try (MetricsTestSupport environment = new MetricsTestSupport()) {
            ChannelMetrics metrics = environment.observe();
            environment.registry.onMeterAdded(_ -> metrics.close());

            failingDispatch(metrics, "orders").run();
            failingDispatch(metrics, "later").run();

            assertThat(environment.timer("orders").count(), is(0L));
            assertThat(environment.counter("orders").count(), is(0L));
            assertThat(environment.registry.timer(DURATION, environment.tags("later")).isEmpty(), is(true));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void metricsWithoutARegistryServiceRemainNoop(boolean enabled) {
        // Missing instrumentation must not affect processing, regardless of the messaging metrics setting.
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .build());
        try {
            var config = MessagingMetricsConfig.builder().enabled(enabled).buildPrototype();
            ChannelMetrics metrics = ChannelMetrics.create(config, manager.registry());
            failingDispatch(metrics, "orders").run();
            metrics.close();
            failingDispatch(metrics, "other").run();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void globalDisableAvoidsClockAndRegistration() {
        // Provider-wide disablement must avoid both registration and timing work in messaging.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.builder().enabled(false).build(),
                                                                    FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            failingDispatch(environment.observe(), "orders").run();
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
        try (MetricsTestSupport environment = new MetricsTestSupport(config, FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            Runnable recorder = failingDispatch(environment.observe(), "orders");
            int clockReads = environment.clock.reads.get();
            recorder.run();

            assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
            assertThat(environment.counter("orders").count(), is(1L));
            assertThat(handler.clockRequests.get(), is(0));
            assertThat(environment.clock.reads.get(), is(clockReads));
        }
    }

    @Test
    void disabledFailureCounterPreservesProcessingCount() {
        // A failed attempt still contributes to the timer when only the failure counter is disabled.
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(FAILURES))).enabled(false))
                .build();
        try (MetricsTestSupport environment = new MetricsTestSupport(config)) {
            failingDispatch(environment.observe(), "orders").run();

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));
        }
    }

    @Test
    void tagFiltersApplyBeforeAcquiringMeters() {
        // The provider can exclude one channel; messaging must apply that decision before registering or timing it.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create(), FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            handler.filteredChannel = "ignored";
            ChannelMetrics metrics = environment.observe();
            failingDispatch(metrics, "ignored").run();
            assertThat(environment.registry.meters(), empty());
            assertThat(handler.clockRequests.get(), is(0));

            failingDispatch(metrics, "orders").run();
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(1L));
        }
    }

    @Test
    void partialRegistrationFailureDoesNotPreventLaterRecording() {
        // Failure of the counter disables this recorder; a later graph reuses the timer and completes registration.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create(), FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            handler.fault = Fault.REGISTER;
            ChannelMetrics first = environment.observe();
            failingDispatch(first, "orders").run();
            assertThat(environment.timer("orders").count(), is(0L));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));

            handler.fault = Fault.NONE;
            Runnable subsequent = successfulDispatch(environment.observe(), "orders");
            first.close();
            subsequent.run();
            assertThat(environment.timer("orders").count(), is(1L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {DURATION, FAILURES})
    void throwingAddListenerPreservesExistingCountsAndAllowsLaterRecording(String failingMeter) {
        // An add listener fails after insertion; the failed recorder stops while a later graph reuses those series.
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig config = MetricsConfig.builder()
                .appName("messaging-tests")
                .tags(List.of(factory.tagCreate("region", "test")))
                .build();
        try (MetricsTestSupport environment = new MetricsTestSupport(config, factory)) {
            ChannelMetrics existing = environment.observe();
            failingDispatch(existing, "payments").run();
            AtomicBoolean failed = new AtomicBoolean();
            environment.registry.onMeterAdded(meter -> {
                if (meter.id().name().equals(failingMeter) && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("Listener failed after registration");
                }
            });

            ChannelMetrics broken = environment.observe();
            failingDispatch(broken, "orders").run();
            assertThat(environment.timer("orders").count(), is(0L));
            assertThat(environment.timer("payments").count(), is(1L));
            assertThat(environment.counter("payments").count(), is(1L));

            ChannelMetrics replacement = environment.observe();
            successfulDispatch(replacement, "orders").run();
            assertThat(environment.timer("orders").count(), is(1L));
            broken.close();
            replacement.close();
            assertThat(environment.registry.meters().size(), is(4));
            existing.close();
            assertThat(environment.registry.meters().size(), is(4));
        }
    }

    @Test
    void recordingFailureDisablesOnlyAffectedRecorder() {
        // One graph's recording failure must not disable another graph that shares its meter registry and channel.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create(), FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            ChannelMetrics first = environment.observe();
            Runnable broken = failingDispatch(first, "orders");
            Runnable surviving = successfulDispatch(environment.observe(), "orders");
            handler.fault = Fault.RECORD;
            broken.run();
            handler.fault = Fault.NONE;
            broken.run();
            surviving.run();

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void clockFailureDisablesRecorder() {
        // A failed clock stops recording for this graph, including after the clock becomes available again.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            ChannelMetrics metrics = environment.observe();
            Runnable recorder = failingDispatch(metrics, "orders");
            environment.clock.fail = true;
            recorder.run();
            environment.clock.fail = false;
            recorder.run();
            assertThat(environment.timer("orders").count(), is(0L));
            metrics.close();
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void closeNeverRemovesRegistryMetersAndStopsLateRecording() {
        // Closing an observer never asks the registry to delete a series, and an already-started sample is discarded.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create(), FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            ChannelMetrics metrics = environment.observe();
            var failure = new IllegalStateException("Delivery failed during close");
            var dispatch = metrics.observe("orders", _ -> {
                metrics.close();
                throw failure;
            });
            assertThat(assertThrows(IllegalStateException.class, () -> dispatch.accept(BATCH)), sameInstance(failure));
            assertThat(handler.removals.get(), is(0));
            assertThat(environment.timer("orders").count(), is(0L));

            ChannelMetrics replacement = environment.observe();
            successfulDispatch(replacement, "orders").run();
            replacement.close();
            assertThat(handler.removals.get(), is(0));
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    private static Runnable successfulDispatch(ChannelMetrics metrics, String channel) {
        var dispatch = metrics.observe(channel, _ -> { });
        return () -> dispatch.accept(BATCH);
    }

    private static Runnable failingDispatch(ChannelMetrics metrics, String channel) {
        var failure = new IllegalStateException("Delivery failed");
        var dispatch = metrics.observe(channel, _ -> {
            throw failure;
        });
        return () -> assertThat(assertThrows(IllegalStateException.class, () -> dispatch.accept(BATCH)), sameInstance(failure));
    }

    private enum Fault {
        NONE,
        REGISTER,
        RECORD
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
        @SuppressWarnings("unchecked")
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
            if (builder instanceof Counter.Builder && fault == Fault.REGISTER) {
                throw new IllegalStateException("Counter registration unavailable");
            }
            M result = super.getOrCreate(builder);
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
            throw new AssertionError("Graph close must not remove registry meters");
        }
    }
}
