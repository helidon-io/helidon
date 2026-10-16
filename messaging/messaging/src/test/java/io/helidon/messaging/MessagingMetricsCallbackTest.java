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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsCallbackTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void addListenerCanAwaitChildGraphDelivery() throws Exception {
        assertCallbackCanAwaitDelivery(false);
    }

    @Test
    void removalListenerCanAwaitChildGraphDelivery() throws Exception {
        assertCallbackCanAwaitDelivery(true);
    }

    @Test
    void addListenerCanAwaitChildGraphClose() throws Exception {
        assertCallbackCanAwaitClose(false);
    }

    @Test
    void removalListenerCanAwaitChildGraphClose() throws Exception {
        assertCallbackCanAwaitClose(true);
    }

    @Test
    void deliverySpanningInitializationKeepsItsOriginalClock() throws Exception {
        TestClock clock = new TestClock();
        try (Environment environment = new Environment(clock)) {
            MessagingChannel<String> channel = MessagingChannel.create("child", String.class);
            AtomicReference<MessagingGraph> child = new AtomicReference<>();
            CountDownLatch handlerEntered = new CountDownLatch(1);
            CountDownLatch releaseHandler = new CountDownLatch(1);
            AtomicBoolean listenerEntered = new AtomicBoolean();
            AtomicBoolean deliveryStartedInsideListener = new AtomicBoolean();
            FutureTask<Void> delivery = new FutureTask<>(() -> {
                MessagingGraph graph = environment.builder().channel(channel)
                        .payloadSink(channel, payload -> {
                            if (payload.equals("spanning-initialization")) {
                                handlerEntered.countDown();
                                await(releaseHandler);
                            } else {
                                clock.nanos.addAndGet(7);
                            }
                        }).build();
                child.set(graph);
                graph.start();
                graph.emitter(channel).emit("spanning-initialization");
            }, null);
            Thread worker = Thread.ofVirtual().unstarted(delivery);
            long before = System.nanoTime();
            try {
                try (MessagingGraph _ = environment.trigger(false, _ -> {
                    if (!listenerEntered.compareAndSet(false, true)) {
                        return;
                    }
                    worker.start();
                    try {
                        deliveryStartedInsideListener.set(handlerEntered.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                })) {
                    assertThat("Child delivery must start before the provider callback returns",
                               deliveryStartedInsideListener.get(),
                               is(true));
                    environment.assertCounts("child", 0, 0);
                    Timer timer = environment.meters.timer(DURATION,
                                                          List.of(environment.factory.tagCreate("channel", "child")))
                            .orElseThrow();
                    releaseHandler.countDown();
                    delivery.get(5, TimeUnit.SECONDS);

                    environment.assertCounts("child", 1, 0);
                    double spanningDuration = timer.totalTime(TimeUnit.NANOSECONDS);
                    assertThat(spanningDuration, greaterThan(0.0));
                    assertThat("Pending and initialized clocks must not be subtracted from one another",
                               spanningDuration,
                               lessThanOrEqualTo((double) (System.nanoTime() - before)));

                    child.get().emitter(channel).emit("after-initialization");
                    environment.assertCounts("child", 2, 0);
                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS), is(spanningDuration + 7));
                }
            } finally {
                releaseHandler.countDown();
                if (listenerEntered.get()) {
                    worker.join(TimeUnit.SECONDS.toMillis(5));
                    assertThat("Child worker must terminate", worker.isAlive(), is(false));
                }
                if (child.get() != null) {
                    child.get().close();
                }
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    @Test
    void pendingSampleOverflowPreservesDeliveryAndParentMetrics() throws Exception {
        try (Environment environment = new Environment();
             ChildCallback callback = new ChildCallback(environment, false, 2_000)) {
            try (MessagingGraph parent = environment.trigger(false, callback).start()) {
                callback.assertCompletedInsideListener();
                assertThat(callback.deliveries.get(), is(2_000));
                environment.assertAbsent("child");

                callback.child.get().emitter(callback.channel).emit("after-overflow");
                assertThat(callback.deliveries.get(), is(2_001));
                environment.assertAbsent("child");

                parent.emitter(environment.parentChannel).emit("parent-still-records");
                environment.assertCounts("parent", 1, 0);
                callback.close();
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    @Test
    void pendingRegistrationOverflowPreservesDelivery() throws Exception {
        try (Environment environment = new Environment()) {
            List<MessagingGraph> children = new ArrayList<>();
            List<MessagingChannel<String>> channels = new ArrayList<>();
            AtomicInteger deliveries = new AtomicInteger();
            AtomicBoolean listenerEntered = new AtomicBoolean();
            AtomicBoolean completedInsideListener = new AtomicBoolean();
            FutureTask<Void> task = new FutureTask<>(() -> {
                for (int i = 0; i < 65; i++) {
                    MessagingChannel<String> channel = MessagingChannel.create("queued-" + i, String.class);
                    MessagingGraph child = environment.builder().channel(channel)
                            .payloadSink(channel, _ -> deliveries.incrementAndGet()).build();
                    children.add(child);
                    channels.add(channel);
                    child.start().emitter(channel).emit("before-registration");
                }
            }, null);
            Thread worker = Thread.ofVirtual().unstarted(task);
            try {
                try (MessagingGraph parent = environment.trigger(false, _ -> {
                    if (!listenerEntered.compareAndSet(false, true)) {
                        return;
                    }
                    worker.start();
                    try {
                        task.get(5, TimeUnit.SECONDS);
                        completedInsideListener.set(true);
                    } catch (TimeoutException _) {
                        // Release the provider operation before joining a worker blocked by a regression.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    } catch (ExecutionException e) {
                        throw new IllegalStateException(e.getCause());
                    }
                }).start()) {
                    task.get(5, TimeUnit.SECONDS);
                    assertThat("All graphs must deliver before the provider callback returns",
                               completedInsideListener.get(), is(true));
                    assertThat(deliveries.get(), is(65));
                    for (int i = 0; i < 64; i++) {
                        environment.assertCounts("queued-" + i, 1, 0);
                    }
                    environment.assertAbsent("queued-64");

                    for (int i = 0; i < children.size(); i++) {
                        children.get(i).emitter(channels.get(i)).emit("after-registration");
                    }
                    assertThat(deliveries.get(), is(130));
                    for (int i = 0; i < 64; i++) {
                        environment.assertCounts("queued-" + i, 2, 0);
                    }
                    environment.assertAbsent("queued-64");
                    parent.emitter(environment.parentChannel).emit("parent-still-records");
                    environment.assertCounts("parent", 1, 0);
                }
            } finally {
                if (listenerEntered.get()) {
                    worker.join(TimeUnit.SECONDS.toMillis(5));
                    assertThat("Child worker must terminate", worker.isAlive(), is(false));
                }
                children.forEach(MessagingGraph::close);
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    @Test
    void childClosedBeforeInitializationReplaysCompletedSamplesIntoSharedMeters() throws Exception {
        try (Environment environment = new Environment();
             ChildCallback callback = new ChildCallback(environment, true, 2, "parent")) {
            try (MessagingGraph parent = environment.trigger(false, callback).start()) {
                callback.assertCompletedInsideListener();
                assertThat(callback.deliveries.get(), is(2));
                environment.assertCounts("parent", 2, 1);

                callback.close();
                parent.emitter(environment.parentChannel).emit("parent-survives-child-close");
                environment.assertCounts("parent", 3, 1);
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Handler must be released", latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void assertCallbackCanAwaitDelivery(boolean removal) throws Exception {
        try (Environment environment = new Environment();
             ChildCallback callback = new ChildCallback(environment, false)) {
            try (MessagingGraph _ = environment.trigger(removal, callback)) {
                callback.assertCompletedInsideListener();
                assertThat(callback.deliveries.get(), is(2));
                environment.assertCounts("child", 2, 1);

                callback.child.get().emitter(callback.channel).emit("after-registration");
                assertThat(callback.deliveries.get(), is(3));
                environment.assertCounts("child", 3, 1);
                callback.close();
                environment.assertAbsent("child");
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    private static void assertCallbackCanAwaitClose(boolean removal) throws Exception {
        try (Environment environment = new Environment();
             ChildCallback callback = new ChildCallback(environment, true)) {
            try (MessagingGraph _ = environment.trigger(removal, callback)) {
                callback.assertCompletedInsideListener();
                assertThat(callback.deliveries.get(), is(2));
                environment.assertAbsent("child");

                MessagingChannel<String> replacementChannel = MessagingChannel.create("child", String.class);
                try (MessagingGraph replacement = environment.builder().channel(replacementChannel)
                        .payloadSink(replacementChannel, _ -> { }).build().start()) {
                    replacement.emitter(replacementChannel).emit("fresh-generation");
                    environment.assertCounts("child", 1, 0);
                }
                environment.assertAbsent("child");
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    private static final class ChildCallback implements Consumer<Meter>, AutoCloseable {
        private final Environment environment;
        private final boolean closeBeforeRegistration;
        private final int deliveriesBeforeRegistration;
        private final MessagingChannel<String> channel;
        private final AtomicReference<MessagingGraph> child = new AtomicReference<>();
        private final AtomicInteger deliveries = new AtomicInteger();
        private final AtomicBoolean entered = new AtomicBoolean();
        private final FutureTask<Void> task;
        private final Thread worker;
        private boolean completedInsideListener;

        private ChildCallback(Environment environment, boolean closeBeforeRegistration) {
            this(environment, closeBeforeRegistration, 2);
        }

        private ChildCallback(Environment environment, boolean closeBeforeRegistration, int deliveriesBeforeRegistration) {
            this(environment, closeBeforeRegistration, deliveriesBeforeRegistration, "child");
        }

        private ChildCallback(Environment environment,
                              boolean closeBeforeRegistration,
                              int deliveriesBeforeRegistration,
                              String channelName) {
            this.environment = environment;
            this.closeBeforeRegistration = closeBeforeRegistration;
            this.deliveriesBeforeRegistration = deliveriesBeforeRegistration;
            channel = MessagingChannel.create(channelName, String.class);
            task = new FutureTask<>(this::runChild, null);
            worker = Thread.ofVirtual().unstarted(task);
        }

        @Override
        public void accept(Meter meter) {
            if (!entered.compareAndSet(false, true)) {
                return;
            }
            worker.start();
            try {
                task.get(5, TimeUnit.SECONDS);
                completedInsideListener = true;
            } catch (TimeoutException _) {
                // Return from the provider callback to break a regression's wait cycle and permit worker cleanup.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the child graph", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Child graph failed", e.getCause());
            }
        }

        @Override
        public void close() throws Exception {
            if (!entered.get()) {
                return;
            }
            try {
                worker.join(TimeUnit.SECONDS.toMillis(5));
                assertThat("Child worker must terminate", worker.isAlive(), is(false));
                task.get(1, TimeUnit.SECONDS);
            } finally {
                MessagingGraph graph = child.get();
                if (graph != null) {
                    graph.close();
                }
            }
        }

        private void assertCompletedInsideListener() throws Exception {
            assertThat("Provider listener must execute", entered.get(), is(true));
            task.get(5, TimeUnit.SECONDS);
            assertThat("Child construction and delivery must complete before the listener returns",
                       completedInsideListener,
                       is(true));
        }

        private void runChild() {
            MessagingGraph graph = environment.builder().channel(channel)
                    .payloadSink(channel, payload -> {
                        deliveries.incrementAndGet();
                        if (payload.equals("fail")) {
                            throw new IllegalStateException("Child processing failed");
                        }
                    }).build();
            child.set(graph);
            graph.start();
            for (int i = 1; i < deliveriesBeforeRegistration; i++) {
                graph.emitter(channel).emit("before-registration");
            }
            assertThrows(BatchDeliveryException.class, () -> graph.emitter(channel).emit("fail"));
            if (closeBeforeRegistration) {
                graph.close();
            }
        }
    }

    private static final class TestClock implements Clock {
        private final AtomicLong nanos = new AtomicLong(Long.MAX_VALUE / 4);

        @Override
        public long wallTime() {
            return TimeUnit.NANOSECONDS.toMillis(nanos.get());
        }

        @Override
        public long monotonicTime() {
            return nanos.get();
        }
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MessagingChannel<String> parentChannel = MessagingChannel.create("parent", String.class);
        private final MeterRegistry meters;
        private final ServiceRegistryManager manager;

        private Environment() {
            this(Optional.empty());
        }

        private Environment(Clock clock) {
            this(Optional.of(clock));
        }

        private Environment(Optional<Clock> clock) {
            meters = factory.createMeterRegistry(clock.orElseGet(factory::clockSystem), MetricsConfig.create());
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

        private MessagingConfig.Builder builder() {
            return MessagingGraph.builder().serviceRegistry(manager.registry());
        }

        private MessagingGraph trigger(boolean removal, Consumer<Meter> callback) {
            if (!removal) {
                meters.onMeterAdded(callback);
            }
            MessagingGraph graph = builder().channel(parentChannel).payloadSink(parentChannel, _ -> { }).build();
            if (removal) {
                meters.onMeterRemoved(callback);
                graph.close();
            }
            return graph;
        }

        private void assertCounts(String channel, long attempts, long failures) {
            var tags = List.of(factory.tagCreate("channel", channel));
            assertThat(meters.timer(DURATION, tags).orElseThrow().count(), is(attempts));
            assertThat(meters.counter(FAILURES, tags).orElseThrow().count(), is(failures));
        }

        private void assertAbsent(String channel) {
            var tags = List.of(factory.tagCreate("channel", channel));
            assertThat(meters.timer(DURATION, tags).isEmpty(), is(true));
            assertThat(meters.counter(FAILURES, tags).isEmpty(), is(true));
        }
    }
}
