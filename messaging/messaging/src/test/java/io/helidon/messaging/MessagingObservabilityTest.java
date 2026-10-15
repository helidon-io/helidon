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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.faulttolerance.Retry;
import io.helidon.health.HealthCheckResponse;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.MessagingConnectorProviderConfig;
import io.helidon.messaging.spi.MessagingIncomingConfig;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class MessagingObservabilityTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void singletonAndBatchEachCountAsOneProcessingAttempt() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            List<List<String>> received = new ArrayList<>();
            try (MessagingGraph graph = fixture.builder().channel(channel).batchSink(channel, batch -> {
                received.add(batch.payloads());
            }).build().start()) {
                graph.emitter(channel).emit("first");
                graph.emitter(channel).emit(batch("second", "third", "fourth"));

                assertThat(received, is(List.of(List.of("first"), List.of("second", "third", "fourth"))));
                fixture.assertCounts("orders", 2, 0);
                assertThat(fixture.timer("orders").totalTime(TimeUnit.NANOSECONDS), greaterThan(0.0));
            }
        }
    }

    @Test
    void durationIncludesSuccessfulAndFailedCallbacksAndExcludesTimeBetweenEmissions() {
        TestClock clock = new TestClock();
        try (Fixture fixture = new Fixture(Optional.of(clock))) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph graph = fixture.builder().channel(channel).payloadSink(channel, payload -> {
                clock.advance(7);
                if (payload.equals("fail")) {
                    throw new IllegalStateException("failed processing");
                }
            }).buildPrototype().build().start()) {
                clock.advance(1_000);
                graph.emitter(channel).emit("success");
                clock.advance(2_000);
                assertThrows(BatchDeliveryException.class, () -> graph.emitter(channel).emit("fail"));
                clock.advance(3_000);

                fixture.assertCounts("orders", 2, 1);
                assertThat(fixture.timer("orders").totalTime(TimeUnit.NANOSECONDS), is(14.0));
            }
        }
    }

    @Test
    void runtimeExceptionCountsAsFailedAttemptAndPreservesCause() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            IllegalStateException expected = new IllegalStateException("failed processing");
            try (MessagingGraph graph = fixture.builder().channel(channel).batchSink(channel, _ -> {
                throw expected;
            }).build().start()) {
                BatchDeliveryException actual = assertThrows(BatchDeliveryException.class,
                                                             () -> graph.emitter(channel).emit(batch("first", "second")));

                assertThat(actual.getCause(), sameInstance(expected));
                fixture.assertCounts("orders", 1, 1);
                assertThat(graph.healthChecks().stream().map(check -> check.call().status()).toList(),
                           is(List.of(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.UP)));
            }
        }
    }

    @Test
    void errorCountsAsFailedAttemptAndPreservesIdentity() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AssertionError expected = new AssertionError("failed processing");
            try (MessagingGraph graph = fixture.builder().channel(channel).batchSink(channel, _ -> {
                throw expected;
            }).build().start()) {
                AssertionError actual = assertThrows(AssertionError.class, () -> graph.emitter(channel).emit("first"));

                assertThat(actual, sameInstance(expected));
                fixture.assertCounts("orders", 1, 1);
            }
        }
    }

    @Test
    void fanOutCountsOnceWhileRoutingCountsBothChannels() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> source = MessagingChannel.create("source", String.class);
            MessagingChannel<String> target = MessagingChannel.create("target", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = fixture.builder().channel(source).channel(target)
                    .payloadSink(source, _ -> deliveries.incrementAndGet())
                    .payloadSink(source, _ -> deliveries.incrementAndGet())
                    .route(source, target)
                    .payloadSink(target, _ -> deliveries.incrementAndGet())
                    .build().start()) {
                graph.emitter(source).emit(batch("first", "second"));

                assertThat(deliveries.get(), is(6));
                fixture.assertCounts("source", 1, 0);
                fixture.assertCounts("target", 1, 0);
            }
        }
    }

    @Test
    void rejectionBeforeDispatchDoesNotCountAsProcessing() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = fixture.builder().maxInFlightMessages(1).channel(channel)
                    .payloadSink(channel, _ -> deliveries.incrementAndGet()).build()) {
                assertThrows(IllegalStateException.class, () -> graph.emitter(channel).emit("before start"));
                graph.start();
                assertThrows(MessagingRejectedException.class, () -> graph.emitter(channel).emit(batch("first", "second")));

                assertThat(deliveries.get(), is(0));
                fixture.assertCounts("orders", 0, 0);
                graph.emitter(channel).emit("accepted");
                fixture.assertCounts("orders", 1, 0);
            }
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void payloadConversionBeforeDispatchDoesNotCountAsProcessing() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph graph = fixture.builder().channel(channel).payloadSink(channel, _ -> { }).build().start()) {
                Emitter emitter = graph.emitter(channel);
                assertThrows(IllegalArgumentException.class, () -> emitter.emit(42));

                fixture.assertCounts("orders", 0, 0);
            }
        }
    }

    @Test
    void messageEnvelopeValidationInsideDispatchCountsAsFailure() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            ConsumerRegistration consumer = new ConsumerRegistration() {
                @Override
                public String channel() {
                    return "orders";
                }

                @Override
                public GenericType<?> payloadGenericType() {
                    return GenericType.create(String.class);
                }

                @Override
                public GenericType<?> envelopeGenericType() {
                    return GenericType.create(SpecialMessage.class);
                }

                @Override
                public void dispatch(MessageBatch<?> batch) {
                    deliveries.incrementAndGet();
                }
            };
            try (MessagingGraph graph = fixture.builder().channel(channel).addConsumerRegistration(consumer).build().start()) {
                assertThrows(IllegalArgumentException.class, () -> graph.emitter(channel).emit("ordinary envelope"));

                assertThat(deliveries.get(), is(0));
                fixture.assertCounts("orders", 1, 1);
            }
        }
    }

    @Test
    void callerCancellationDoesNotCompleteTimingBeforeCallbackReturns() throws InterruptedException {
        TestClock clock = new TestClock();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        try (Fixture fixture = new Fixture(Optional.of(clock))) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph graph = fixture.builder().maxInFlightMessages(1).channel(channel)
                    .payloadSink(channel, payload -> {
                        if (payload.equals("blocked")) {
                            entered.countDown();
                            boolean complete = false;
                            while (!complete) {
                                try {
                                    release.await();
                                    complete = true;
                                } catch (InterruptedException _) {
                                    // Keep the callback active after the caller cancels its wait.
                                }
                            }
                        }
                    }).build().start()) {
                Thread caller = Thread.ofVirtual().start(() -> {
                    try {
                        graph.emitter(channel).emit("blocked");
                    } catch (Throwable failure) {
                        callerFailure.set(failure);
                        callerInterrupted.set(Thread.currentThread().isInterrupted());
                    }
                });
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
                    caller.interrupt();
                    caller.join(TimeUnit.SECONDS.toMillis(5));
                    assertThat(caller.isAlive(), is(false));
                    assertThat(callerFailure.get(), instanceOf(MessagingRejectedException.class));
                    assertThat(callerInterrupted.get(), is(true));
                    fixture.assertCounts("orders", 0, 0);
                    Timer timer = fixture.timer("orders");

                    clock.advance(123);
                    release.countDown();
                    graph.close();

                    assertThat(timer.count(), is(1L));
                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS), is(123.0));
                } finally {
                    release.countDown();
                    caller.interrupt();
                    caller.join(TimeUnit.SECONDS.toMillis(5));
                }
            }
        }
    }

    @Test
    void failedThenSuccessfulRetryRecordsBothAttempts() {
        try (Fixture fixture = new Fixture()) {
            TestIncoming incoming = new TestIncoming();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger attempts = new AtomicInteger();
            try (MessagingGraph graph = fixture.incomingBuilder(incoming, 2).channel(channel).batchSink(channel, _ -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("retry this batch");
                }
            }).build().start()) {
                incoming.deliver(batch("first", "second"));

                assertThat(attempts.get(), is(2));
                fixture.assertCounts("orders", 2, 1);
            }
        }
    }

    @Test
    void retryExhaustionCountsEveryFailedAttempt() {
        try (Fixture fixture = new Fixture()) {
            TestIncoming incoming = new TestIncoming();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger attempts = new AtomicInteger();
            try (MessagingGraph graph = fixture.incomingBuilder(incoming, 3).channel(channel).batchSink(channel, _ -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("always fails");
            }).build().start()) {
                incoming.deliver(batch("first", "second"));

                assertThat(attempts.get(), is(3));
                fixture.assertCounts("orders", 3, 3);
                assertThat(graph.healthChecks().stream().map(check -> check.call().status()).toList(),
                           is(List.of(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.UP)));
            }
        }
    }

    @Test
    void failedAndDeferredSubsetsEachCountWhenDispatched() {
        try (Fixture fixture = new Fixture()) {
            TestIncoming incoming = new TestIncoming();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            List<List<String>> attempts = new ArrayList<>();
            IllegalStateException failure = new IllegalStateException("retry second message");
            try (MessagingGraph graph = fixture.incomingBuilder(incoming, 2).channel(channel).batchSink(channel, current -> {
                attempts.add(current.payloads());
                if (attempts.size() == 1) {
                    throw new BatchDeliveryException("partial failure", failure, current,
                                                     List.of(BatchItemOutcome.succeeded(0),
                                                             BatchItemOutcome.failed(1, failure),
                                                             BatchItemOutcome.notAttempted(2)));
                }
            }).build().start()) {
                incoming.deliver(batch("first", "second", "third"));

                assertThat(attempts, is(List.of(List.of("first", "second", "third"), List.of("second"), List.of("third"))));
                fixture.assertCounts("orders", 3, 1);
            }
        }
    }

    @Test
    void connectorMappingFailureDoesNotCountAsProcessing() {
        try (Fixture fixture = new Fixture()) {
            TestIncoming incoming = new TestIncoming();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = fixture.incomingBuilder(incoming, 2).channel(channel)
                    .payloadSink(channel, _ -> deliveries.incrementAndGet()).build().start()) {
                try (ConnectorDeliveryReservation reservation = incoming.context.reserveDelivery();
                     ConnectorDelivery delivery = reservation.startFailed(batch("first"),
                                                                           new IllegalArgumentException("mapping"))) {
                    delivery.await();
                }

                assertThat(deliveries.get(), is(0));
                fixture.assertCounts("orders", 0, 0);
            }
        }
    }

    @Test
    void disablingMetricsPreservesDeliveryAndFailuresWhileHealthRemainsEnabled() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            List<String> received = new ArrayList<>();
            IllegalStateException expected = new IllegalStateException("rejected");
            try (MessagingGraph graph = fixture.builder().enableMetrics(false).channel(channel).payloadSink(channel, message -> {
                received.add(message);
                if (message.equals("fail")) {
                    throw expected;
                }
            }).build().start()) {
                graph.emitter(channel).emit("success");
                BatchDeliveryException actual = assertThrows(BatchDeliveryException.class,
                                                             () -> graph.emitter(channel).emit("fail"));

                assertThat(actual.getCause(), sameInstance(expected));
                assertThat(received, is(List.of("success", "fail")));
                fixture.assertMetersAbsent("orders");
                assertThat(graph.healthChecks().size(), is(2));
            }
        }
    }

    @Test
    void disablingHealthLeavesMetricsEnabled() {
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph graph = fixture.builder().enableHealth(false).channel(channel).payloadSink(channel, _ -> { })
                    .build().start()) {
                graph.emitter(channel).emit("first");

                assertThat(graph.healthChecks(), empty());
                fixture.assertCounts("orders", 1, 0);
            }
        }
    }

    @Test
    void configurationCanDisableBothObservers() {
        Config config = Config.just(ConfigSources.create(Map.of("enable-metrics", "false", "enable-health", "false")));
        try (Fixture fixture = new Fixture()) {
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = fixture.builder().config(config).channel(channel)
                    .payloadSink(channel, _ -> deliveries.incrementAndGet()).build().start()) {
                graph.emitter(channel).emit("first");

                assertThat(deliveries.get(), is(1));
                assertThat(graph.healthChecks(), empty());
                fixture.assertMetersAbsent("orders");
            }
        }
    }

    private static MessageBatch<String> batch(String... values) {
        return MessageBatch.create(List.of(values).stream().map(Message::create).toList());
    }

    private interface SpecialMessage extends Message<String> {
    }

    private static final class Fixture implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters;
        private final ServiceRegistryManager services;

        private Fixture() {
            this(Optional.empty());
        }

        private Fixture(Optional<Clock> clock) {
            meters = factory.createMeterRegistry(clock.orElseGet(factory::clockSystem), MetricsConfig.create());
            services = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .putContractInstance(MeterRegistry.class, meters).build());
        }

        @Override
        public void close() {
            try {
                services.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingConfig.Builder builder() {
            return MessagingGraph.builder().serviceRegistry(services.registry());
        }

        private MessagingConfig.Builder incomingBuilder(TestIncoming incoming, int calls) {
            return builder().addConnector(incoming).incoming(Map.of("orders", MessagingIncomingConfig.builder()
                    .channelName("orders")
                    .connector("test")
                    .failure(MessagingFailureConfig.builder()
                                     .retry(Retry.builder().calls(calls).delay(Duration.ZERO).build())
                                     .onExhausted(FailureDisposition.DROP)
                                     .build())
                    .build()));
        }

        private Timer timer(String channel) {
            return meters.timer(DURATION, List.of(factory.tagCreate("channel", channel))).orElseThrow();
        }

        private void assertCounts(String channel, long attempts, long failures) {
            assertThat(channel + " processing attempts", timer(channel).count(), is(attempts));
            assertThat(channel + " processing failures",
                       meters.counter(FAILURES, List.of(factory.tagCreate("channel", channel))).orElseThrow().count(),
                       is(failures));
        }

        private void assertMetersAbsent(String channel) {
            assertThat(meters.timer(DURATION, List.of(factory.tagCreate("channel", channel))).isEmpty(), is(true));
            assertThat(meters.counter(FAILURES, List.of(factory.tagCreate("channel", channel))).isEmpty(), is(true));
        }
    }

    private static final class TestIncoming implements MessagingConnector {
        private final CountDownLatch stopped = new CountDownLatch(1);
        private IncomingConnectorContext context;

        @Override
        public String type() {
            return "test";
        }

        @Override
        public MessagingConnectorProviderConfig prototype() {
            return RegistryTestSupport.prototype("test");
        }

        @Override
        public Optional<IncomingChannel> incoming(MessagingIncomingConfig config) {
            return Optional.of(new IncomingChannel() {
                @Override
                public void run(IncomingConnectorContext connectorContext) {
                    context = connectorContext;
                    if (context.awaitRunning()) {
                        try {
                            stopped.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                @Override
                public void drain() {
                    stopped.countDown();
                }

                @Override
                public void forceClose() {
                    stopped.countDown();
                }

                @Override
                public void close() {
                    stopped.countDown();
                }
            });
        }

        private void deliver(MessageBatch<?> batch) {
            try (ConnectorDeliveryReservation reservation = context.reserveDelivery();
                 ConnectorDelivery delivery = reservation.start(batch)) {
                delivery.await();
            }
        }
    }

    private static final class TestClock implements Clock {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long wallTime() {
            return TimeUnit.NANOSECONDS.toMillis(nanos.get());
        }

        @Override
        public long monotonicTime() {
            return nanos.get();
        }

        private void advance(long elapsed) {
            nanos.addAndGet(elapsed);
        }
    }
}
