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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @ParameterizedTest
    @ValueSource(longs = {0, -100})
    void recordsCompletedAttemptsUsingOwningClock(long startingNanos) {
        // Advance only the selected registry's clock, so elapsed time cannot come from a global clock.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            environment.clock.advance(startingNanos);
            MessagingMetrics metrics = environment.observe();
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
            MessagingMetrics.Recorder again = metrics.channel("orders");
            again.finish(again.start(), false);
            assertThat(timer.count(), is(3L));
            metrics.close();
            assertThat(environment.timer("orders").count(), is(3L));
        }
    }

    @Test
    void sharesCumulativeMetersAcrossGraphCloseAndRecreation() {
        // Closing a graph stops its recording, while other and recreated graphs continue the registry's series.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            MessagingMetrics first = environment.observe();
            MessagingMetrics second = environment.observe();
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
            assertThat(environment.timer("orders").count(), is(2L));

            MessagingMetrics replacement = environment.observe();
            MessagingMetrics.Recorder current = replacement.channel("orders");
            retired.finish(late, true);
            surviving.finish(late, true);
            current.finish(current.start(), false);
            assertThat(environment.timer("orders").count(), is(3L));
            assertThat(environment.counter("orders").count(), is(1L));
        }
    }

    @Test
    void separateMeterRegistriesRemainIndependent() {
        // Equal channel names in different meter registries must not share counts.
        try (MetricsTestSupport first = new MetricsTestSupport(MetricsConfig.create());
             MetricsTestSupport second = new MetricsTestSupport(MetricsConfig.create())) {
            MessagingMetrics firstMetrics = first.observe();
            MessagingMetrics.Recorder firstRecorder = firstMetrics.channel("orders");
            MessagingMetrics.Recorder secondRecorder = second.observe().channel("orders");
            firstRecorder.finish(firstRecorder.start(), true);
            secondRecorder.finish(secondRecorder.start(), false);
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
                    MessagingMetrics.Recorder recursive = first.observe().channel("recursive");
                    recursive.finish(recursive.start(), true);
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
                    assertThrows(AssertionError.class, () -> second.observe().channel("orders"));
                    MessagingMetrics.Recorder retry = second.observe().channel("orders");
                    retry.finish(retry.start(), true);
                    MessagingMetrics.Recorder recursive = first.observe().channel("recursive");
                    recursive.finish(recursive.start(), true);
                }
            });

            MessagingMetrics.Recorder outer = first.observe().channel("orders");
            outer.finish(outer.start(), false);
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
            AtomicReference<MessagingMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    MessagingMetrics metrics = environment.observe();
                    nested.set(metrics);
                    MessagingMetrics.Recorder recorder = metrics.channel("orders");
                    recorder.finish(recorder.start(), true);
                }
            });

            MessagingMetrics outer = environment.observe();
            MessagingMetrics.Recorder recorder = outer.channel("orders");
            nested.get().close();
            recorder.finish(recorder.start(), false);
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
            MessagingMetrics previous = environment.observe();
            MessagingMetrics.Recorder closed = previous.channel("previous");
            AtomicBoolean entered = new AtomicBoolean();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    previous.close();
                }
            });

            MessagingMetrics next = environment.observe();
            MessagingMetrics.Recorder recorder = next.channel("orders");
            recorder.finish(recorder.start(), false);
            closed.finish(closed.start(), true);
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
            MessagingMetrics metrics = environment.observe();
            environment.registry.onMeterAdded(_ -> metrics.close());

            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            MessagingMetrics.Recorder afterClose = metrics.channel("later");
            afterClose.finish(afterClose.start(), true);

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
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                    .serviceRegistry(manager.registry()).metrics(m -> m.enabled(enabled)).buildPrototype(), manager.registry());
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            metrics.close();
            MessagingMetrics.Recorder other = metrics.channel("other");
            other.finish(other.start(), true);
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
            MessagingMetrics.Recorder recorder = environment.observe().channel("orders");
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
        try (MetricsTestSupport environment = new MetricsTestSupport(config, FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            MessagingMetrics.Recorder recorder = environment.observe().channel("orders");
            int clockReads = environment.clock.reads.get();
            recorder.finish(recorder.start(), true);

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
            MessagingMetrics.Recorder recorder = environment.observe().channel("orders");
            recorder.finish(recorder.start(), true);

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
            MessagingMetrics metrics = environment.observe();
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
    void partialRegistrationFailureDoesNotPreventLaterRecording() {
        // Failure of the counter disables this recorder; a later graph reuses the timer and completes registration.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create(), FaultyRegistry::new)) {
            FaultyRegistry handler = (FaultyRegistry) environment.registry;
            handler.fault = Fault.REGISTER;
            MessagingMetrics first = environment.observe();
            MessagingMetrics.Recorder failed = first.channel("orders");
            failed.finish(failed.start(), true);
            assertThat(environment.timer("orders").count(), is(0L));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));

            handler.fault = Fault.NONE;
            MessagingMetrics.Recorder subsequent = environment.observe().channel("orders");
            first.close();
            subsequent.finish(subsequent.start(), false);
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
            MessagingMetrics existing = environment.observe();
            MessagingMetrics.Recorder payments = existing.channel("payments");
            payments.finish(payments.start(), true);
            AtomicBoolean failed = new AtomicBoolean();
            environment.registry.onMeterAdded(meter -> {
                if (meter.id().name().equals(failingMeter) && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("Listener failed after registration");
                }
            });

            MessagingMetrics broken = environment.observe();
            MessagingMetrics.Recorder failedRecorder = broken.channel("orders");
            failedRecorder.finish(failedRecorder.start(), true);
            assertThat(environment.timer("orders").count(), is(0L));
            assertThat(environment.timer("payments").count(), is(1L));
            assertThat(environment.counter("payments").count(), is(1L));

            MessagingMetrics replacement = environment.observe();
            MessagingMetrics.Recorder recorder = replacement.channel("orders");
            recorder.finish(recorder.start(), false);
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
            MessagingMetrics first = environment.observe();
            MessagingMetrics.Recorder broken = first.channel("orders");
            MessagingMetrics.Recorder surviving = environment.observe().channel("orders");
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
    void clockFailureDisablesRecorder() {
        // A failed clock stops recording for this graph, including after the clock becomes available again.
        try (MetricsTestSupport environment = new MetricsTestSupport(MetricsConfig.create())) {
            MessagingMetrics metrics = environment.observe();
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            environment.clock.fail = true;
            recorder.finish(recorder.start(), true);
            environment.clock.fail = false;
            recorder.finish(recorder.start(), true);
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
            MessagingMetrics metrics = environment.observe();
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            var started = recorder.start();
            metrics.close();
            assertThat(handler.removals.get(), is(0));
            recorder.finish(started, true);
            assertThat(environment.timer("orders").count(), is(0L));

            MessagingMetrics replacement = environment.observe();
            MessagingMetrics.Recorder current = replacement.channel("orders");
            current.finish(current.start(), false);
            replacement.close();
            assertThat(handler.removals.get(), is(0));
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
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
