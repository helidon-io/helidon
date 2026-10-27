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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.messaging.spi.OutgoingChannel;
import io.helidon.metrics.api.Counter;
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsLifecycleTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addListenerCanAwaitConcurrentGraphClose(boolean sharedRegistry) throws InterruptedException {
        // The add callback waits for shutdown on another thread, with both shared and independent meter registries.
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch closeEntered = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicReference<MessagingGraph> newGraph = new AtomicReference<>();
        try (Environment registering = new Environment(); Environment independent = new Environment()) {
            Environment closing = sharedRegistry ? registering : independent;
            MessagingChannel<String> existingChannel = MessagingChannel.create("existing", String.class);
            try (MessagingGraph existing = closing.graph(closing.builder().channel(existingChannel)
                    .outgoingChannel(existingChannel, new CloseSignal(closeEntered))).start()) {
                registering.meters.onMeterAdded(_ -> {
                    if (first.compareAndSet(true, false)) {
                        listenerEntered.countDown();
                        await(closeEntered, "Existing graph did not reach connector close");
                        existing.close();
                    }
                });
                Task builder = new Task(() -> {
                    MessagingChannel<String> channel = MessagingChannel.create("new", String.class);
                    newGraph.set(registering.graph(registering.builder().channel(channel).payloadSink(channel, _ -> { })));
                });
                Task closer = new Task(existing::close);
                try {
                    builder.start();
                    await(listenerEntered, "New graph did not register its meters");
                    closer.start();
                    builder.awaitCompletion("Graph construction blocked in its meter listener");
                    closer.awaitCompletion("Graph close blocked in the metrics provider");
                } finally {
                    // Release a listener waiting for shutdown if the lock-cycle regression returns.
                    builder.interrupt();
                    builder.join();
                    closer.join();
                    if (newGraph.get() != null) {
                        newGraph.get().close();
                    }
                }
            }
            assertThat(registering.meters.meters().size(), is(sharedRegistry ? 4 : 2));
            assertThat(closing.meters.meters().size(), is(sharedRegistry ? 4 : 2));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void customizedChannelsAggregateAcrossGraphsInEitherCloseOrder(boolean closeFirst) {
        // Removing channel tags makes distinct channels share counts, which persist after both graphs close.
        HelidonMetricsFactory factory = HelidonMetricsFactory.create(builder -> builder
                .addMeterBuilderCustomizer(meter -> meter.tags(List.of())));
        try (Environment environment = new Environment(factory)) {
            MessagingChannel<String> orders = MessagingChannel.create("orders", String.class);
            MessagingChannel<String> payments = MessagingChannel.create("payments", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph first = environment.graph(environment.builder().channel(orders)
                    .payloadSink(orders, payload -> handle(payload, deliveries))).start();
                 MessagingGraph second = environment.graph(environment.builder().channel(payments)
                    .payloadSink(payments, payload -> handle(payload, deliveries))).start()) {
                first.emitter(orders).emit("first");
                second.emitter(payments).emit("second");
                Timer sharedTimer = environment.meters.timer(DURATION, List.of()).orElseThrow();
                assertThat(sharedTimer.count(), is(2L));

                Emitter<String> surviving;
                if (closeFirst) {
                    first.close();
                    surviving = second.emitter(payments);
                } else {
                    second.close();
                    surviving = first.emitter(orders);
                }
                surviving.emit("third");
                assertThrows(BatchDeliveryException.class, () -> surviving.emit("fail"));

                assertThat(environment.meters.timer(DURATION, List.of()).orElseThrow().count(), is(4L));
                assertThat(environment.meters.counter(FAILURES, List.of()).orElseThrow().count(), is(1L));
                assertThat(deliveries.get(), is(4));
            }
            assertThat(environment.meters.timer(DURATION, List.of()).orElseThrow().count(), is(4L));
            assertThat(environment.meters.counter(FAILURES, List.of()).orElseThrow().count(), is(1L));
        }
    }

    @Test
    void sharedTimerKeepsFailureCountsSeparateByChannel() {
        // Only the timer loses its channel tag; failure counters retain per-channel values across graph close.
        HelidonMetricsFactory factory = HelidonMetricsFactory.create(builder -> builder
                .addMeterBuilderCustomizer(meter -> {
                    if (meter.name().equals(DURATION)) {
                        meter.tags(List.of());
                    }
                }));
        try (Environment environment = new Environment(factory)) {
            MessagingChannel<String> orders = MessagingChannel.create("orders", String.class);
            MessagingChannel<String> payments = MessagingChannel.create("payments", String.class);
            var ordersTags = List.of(factory.tagCreate("channel", orders.name()));
            var paymentsTags = List.of(factory.tagCreate("channel", payments.name()));
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph first = environment.graph(environment.builder().channel(orders)
                    .payloadSink(orders, payload -> handle(payload, deliveries))).start();
                 MessagingGraph second = environment.graph(environment.builder().channel(payments)
                    .payloadSink(payments, payload -> handle(payload, deliveries))).start()) {
                first.emitter(orders).emit("first");
                second.emitter(payments).emit("second");
                Timer sharedTimer = environment.meters.timer(DURATION, List.of()).orElseThrow();
                Counter survivingCounter = environment.meters.counter(FAILURES, paymentsTags).orElseThrow();
                assertThat(environment.meters.counter(FAILURES, ordersTags).isPresent(), is(true));
                first.close();

                assertThat(environment.meters.counter(FAILURES, ordersTags).orElseThrow().count(), is(0L));
                assertThrows(BatchDeliveryException.class, () -> second.emitter(payments).emit("fail"));
                assertThat(sharedTimer.count(), is(3L));
                assertThat(survivingCounter.count(), is(1L));
                assertThat(deliveries.get(), is(3));
            }
            assertThat(environment.meters.timer(DURATION, List.of()).orElseThrow().count(), is(3L));
            assertThat(environment.meters.counter(FAILURES, ordersTags).orElseThrow().count(), is(0L));
            assertThat(environment.meters.counter(FAILURES, paymentsTags).orElseThrow().count(), is(1L));
        }
    }

    @Test
    void registrationTypeMismatchPreservesTheExistingMeterAndDelivery() {
        // An application already owns a counter with the timer's name; failed observation must preserve it and delivery.
        try (Environment environment = new Environment()) {
            var tags = List.of(environment.factory.tagCreate("channel", "orders"));
            Counter existing = environment.meters.getOrCreate(environment.factory.counterBuilder(DURATION).tags(tags));
            existing.increment();
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = environment.graph(environment.builder().channel(channel)
                    .payloadSink(channel, _ -> deliveries.incrementAndGet())).start()) {
                graph.emitter(channel).emit("first");

                assertThat(deliveries.get(), is(1));
                assertThat(environment.meters.counter(DURATION, tags).orElseThrow(), sameInstance(existing));
                assertThat(existing.count(), is(1L));
            }
            assertThat(environment.meters.counter(DURATION, tags).orElseThrow(), sameInstance(existing));
            existing.increment();
            assertThat(existing.count(), is(2L));
        }
    }

    @Test
    void graphClosePreservesAnApplicationTimer() {
        // Messaging shares an application timer; closing both graphs leaves its cumulative values usable.
        try (Environment environment = new Environment()) {
            var tags = List.of(environment.factory.tagCreate("channel", "orders"));
            Timer existing = environment.meters.getOrCreate(environment.factory.timerBuilder(DURATION).tags(tags));
            existing.record(10, TimeUnit.NANOSECONDS);
            MessagingChannel<String> firstChannel = MessagingChannel.create("orders", String.class);
            MessagingChannel<String> secondChannel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph first = environment.graph(environment.builder().channel(firstChannel)
                    .payloadSink(firstChannel, _ -> { })).start();
                 MessagingGraph second = environment.graph(environment.builder().channel(secondChannel)
                    .payloadSink(secondChannel, _ -> { })).start()) {
                first.emitter(firstChannel).emit("first");
                first.close();
                second.emitter(secondChannel).emit("second");

                assertThat(environment.meters.timer(DURATION, tags).orElseThrow(), sameInstance(existing));
                assertThat(existing.count(), is(3L));
            }
            assertThat(environment.meters.timer(DURATION, tags).orElseThrow(), sameInstance(existing));
            assertThat(environment.meters.counter(FAILURES, tags).orElseThrow().count(), is(0L));
            existing.record(20, TimeUnit.NANOSECONDS);
            assertThat(existing.count(), is(4L));
        }
    }

    @Test
    void failedCounterRegistrationPreservesApplicationAndConflictingMeters() {
        // Counter registration fails after getting the application timer; both existing series remain unaffected.
        try (Environment environment = new Environment()) {
            var tags = List.of(environment.factory.tagCreate("channel", "orders"));
            Timer borrowed = environment.meters.getOrCreate(environment.factory.timerBuilder(DURATION).tags(tags));
            Timer conflicting = environment.meters.getOrCreate(environment.factory.timerBuilder(FAILURES).tags(tags));
            borrowed.record(10, TimeUnit.NANOSECONDS);
            conflicting.record(20, TimeUnit.NANOSECONDS);
            MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
            AtomicInteger deliveries = new AtomicInteger();
            try (MessagingGraph graph = environment.graph(environment.builder().channel(channel)
                    .payloadSink(channel, _ -> deliveries.incrementAndGet())).start()) {
                graph.emitter(channel).emit("first");

                assertThat(deliveries.get(), is(1));
                assertThat(environment.meters.timer(DURATION, tags).orElseThrow(), sameInstance(borrowed));
                assertThat(environment.meters.timer(FAILURES, tags).orElseThrow(), sameInstance(conflicting));
            }
            assertThat(environment.meters.timer(DURATION, tags).orElseThrow(), sameInstance(borrowed));
            assertThat(environment.meters.timer(FAILURES, tags).orElseThrow(), sameInstance(conflicting));
            assertThat(borrowed.count(), is(1L));
            assertThat(conflicting.count(), is(1L));
        }
    }

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            assertThat(failureMessage, latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage, e);
        }
    }

    private static void handle(String payload, AtomicInteger deliveries) {
        deliveries.incrementAndGet();
        if (payload.equals("fail")) {
            throw new IllegalStateException("Processing failed");
        }
    }

    private static final class Task {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final Thread thread;
        private volatile Throwable failure;

        private Task(Runnable action) {
            thread = Thread.ofVirtual().unstarted(() -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    failure = t;
                } finally {
                    completed.countDown();
                }
            });
        }

        private void start() {
            thread.start();
        }

        private void awaitCompletion(String failureMessage) {
            await(completed, failureMessage);
            assertThat(failureMessage, failure, nullValue());
        }

        private void interrupt() {
            thread.interrupt();
        }

        private void join() throws InterruptedException {
            thread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("Test task must terminate", thread.isAlive(), is(false));
        }
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory;
        private final MeterRegistry meters;
        private final ServiceRegistryManager manager;

        private Environment() {
            this(HelidonMetricsFactory.create());
        }

        private Environment(HelidonMetricsFactory factory) {
            this.factory = factory;
            meters = factory.createMeterRegistry(MetricsConfig.create());
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .putContractInstance(MeterRegistry.class, meters)
                    .build());
        }

        @Override
        public void close() {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingGraph graph(MessagingConfig.Builder config) {
            // The fixture owns this registry; graph close releases only this graph's observers.
            return new MessagingGraphAssembler(config.buildPrototype(), manager.registry()).graph();
        }

        private MessagingConfig.Builder builder() {
            return MessagingGraph.builder().serviceRegistry(manager.registry());
        }
    }

    private static final class CloseSignal implements OutgoingChannel {
        private final CountDownLatch closeEntered;

        private CloseSignal(CountDownLatch closeEntered) {
            this.closeEntered = closeEntered;
        }

        @Override
        public void start() {
        }

        @Override
        public void sendBatch(MessageBatch<?> batch) {
        }

        @Override
        public void forceClose() {
            closeEntered.countDown();
        }

        @Override
        public void close() {
            closeEntered.countDown();
        }
    }
}
