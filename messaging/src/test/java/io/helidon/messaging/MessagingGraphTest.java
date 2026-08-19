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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class MessagingGraphTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void preparesAndWaitsForEveryManagedSourceBeforeAdmission() {
        List<String> events = new CopyOnWriteArrayList<>();
        AtomicBoolean firstReady = new AtomicBoolean();
        AtomicBoolean secondReady = new AtomicBoolean();
        BooleanSupplier allReady = () -> firstReady.get() && secondReady.get();
        ManagedSource first = ManagedSource.running("first", events, firstReady, allReady);
        ManagedSource second = ManagedSource.running("second", events, secondReady, allReady);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("first", first);
        graph.addIncomingConnector("second", second);

        graph.start();
        awaitCondition(() -> events.contains("admit-first") && events.contains("admit-second"));

        assertEquals(DefaultMessagingGraph.State.RUNNING, graph.state());
        assertTrue(first.prepared());
        assertTrue(second.prepared());
        int lastReady = Math.max(events.indexOf("ready-first"), events.indexOf("ready-second"));
        assertTrue(lastReady < events.indexOf("admit-first"), events.toString());
        assertTrue(lastReady < events.indexOf("admit-second"), events.toString());

        graph.close();
        graph.close();
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
        assertEquals(List.of("close-second", "close-first"), lifecycleEvents(events));
    }

    @Test
    void startsOutgoingBeforeIncomingAndWaitsForCheckpointBeforeClose() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        AtomicBoolean outgoingReady = new AtomicBoolean();
        CountDownLatch incomingRunning = new CountDownLatch(1);
        CountDownLatch stopIncoming = new CountDownLatch(1);
        CountDownLatch admissionStopped = new CountDownLatch(1);
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        CountDownLatch deliveryFinished = new CountDownLatch(1);
        OutgoingConnector outgoing = new OutgoingConnector() {
            @Override
            public void start() {
                events.add("start-outgoing");
                outgoingReady.set(true);
            }

            @Override
            public void sendBatch(MessageBatch<?> batch) {
            }

            @Override
            public void forceClose() {
                events.add("force-outgoing");
            }

            @Override
            public void close() {
                events.add("close-outgoing");
            }
        };
        IncomingConnector incoming = new IncomingConnector() {
            @Override
            public void run(IncomingConnectorContext context) {
                if (!outgoingReady.get()) {
                    throw new AssertionError("Incoming connector ran before outgoing readiness");
                }
                events.add("run-incoming");
                incomingRunning.countDown();
                events.add("ready-incoming");
                if (!context.awaitRunning()) {
                    return;
                }
                events.add("admit-incoming");
                await(stopIncoming);
                await(deliveryFinished);
                events.add("checkpoint-incoming");
            }

            @Override
            public void drain() {
                events.add("stop-incoming");
                admissionStopped.countDown();
                stopIncoming.countDown();
            }

            @Override
            public void forceClose() {
                events.add("force-incoming");
                stopIncoming.countDown();
            }

            @Override
            public void close() {
                events.add("close-incoming");
                stopIncoming.countDown();
            }
        };
        MessagingExecutionConfig config = config(SHUTDOWN_TIMEOUT);
        DeliveryEngine engine = engine(config, "orders");
        DefaultMessagingGraph graph = new DefaultMessagingGraph(engine);
        graph.addBinding(outgoing);
        graph.addIncomingConnector("incoming", incoming);

        graph.start();
        awaitCondition(() -> events.contains("admit-incoming"));

        assertEquals(List.of("start-outgoing",
                             "run-incoming",
                             "ready-incoming",
                             "admit-incoming"),
                     List.copyOf(events));

        AsyncTask delivery = async(() -> engine.dispatch("orders",
                                                         MessageBatch.create(List.of(message("order"))),
                                                         () -> {
            events.add("delivery-start");
            deliveryStarted.countDown();
            await(releaseDelivery);
            events.add("delivery-end");
            deliveryFinished.countDown();
        }));
        await(deliveryStarted);
        AsyncTask closing = async(graph::close);
        await(admissionStopped);

        assertFalse(events.contains("checkpoint-incoming"), "Incoming connector checkpointed before runtime drain");

        releaseDelivery.countDown();
        awaitSuccess(delivery);
        awaitSuccess(closing);

        assertEquals(List.of("start-outgoing",
                             "run-incoming",
                             "ready-incoming",
                             "admit-incoming",
                             "delivery-start",
                             "stop-incoming",
                             "delivery-end",
                             "checkpoint-incoming",
                             "close-incoming",
                             "close-outgoing"),
                     events);
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
    }

    @Test
    void dualConnectorRegisteredAsOutgoingUsesOnlyOutgoingLifecycle() {
        List<String> events = new CopyOnWriteArrayList<>();
        DualConnector connector = new DualConnector(events);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addBinding(connector);

        graph.start();
        graph.close();

        assertEquals(List.of("outgoing-start", "close"), events);
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
    }

    @Test
    void dualConnectorRegisteredAsIncomingUsesOnlyIncomingLifecycle() {
        List<String> events = new CopyOnWriteArrayList<>();
        DualConnector connector = new DualConnector(events);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("dual", connector);

        graph.start();
        awaitCondition(() -> events.contains("incoming-admit"));
        graph.close();

        assertEquals(List.of("incoming-run",
                             "incoming-ready",
                             "incoming-admit",
                             "incoming-stop",
                             "incoming-checkpoint",
                             "close"),
                     events);
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
    }

    @Test
    void forcedCleanupOfDualConnectorUsesItsRegistrationRole() {
        List<String> outgoingEvents = new CopyOnWriteArrayList<>();
        DualConnector outgoing = new DualConnector(outgoingEvents);
        DefaultMessagingGraph outgoingGraph = graph(config(SHUTDOWN_TIMEOUT));
        outgoingGraph.addBinding(outgoing);

        outgoingGraph.close();

        assertEquals(List.of("force-close", "close"), outgoingEvents);

        List<String> incomingEvents = new CopyOnWriteArrayList<>();
        DualConnector incoming = new DualConnector(incomingEvents);
        DefaultMessagingGraph incomingGraph = graph(config(SHUTDOWN_TIMEOUT));
        incomingGraph.addIncomingConnector("dual", incoming);

        incomingGraph.close();

        assertEquals(List.of("force-close", "close"), incomingEvents);
    }

    @Test
    void concurrentStartCallersShareOneSuccessfulStartup() throws Exception {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        StartupBlockingSource source = new StartupBlockingSource();
        graph.addIncomingConnector("source", source);

        AsyncTask owner = async(graph::start);
        await(source.running());
        awaitState(graph, DefaultMessagingGraph.State.STARTING);
        AsyncTask waiter = async(graph::start);
        awaitWaiting(waiter);

        source.releaseStartup();
        awaitSuccess(owner);
        awaitSuccess(waiter);

        assertEquals(DefaultMessagingGraph.State.RUNNING, graph.state());
        assertEquals(1, source.runCalls());
        graph.close();
    }

    @Test
    void outgoingStartFailurePreventsIncomingTasksAndRollsBackConnectors() {
        IllegalStateException startupFailure = new IllegalStateException("outgoing is not ready");
        AtomicInteger forceCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        OutgoingConnector outgoing = new OutgoingConnector() {
            @Override
            public void start() {
                throw startupFailure;
            }

            @Override
            public void sendBatch(MessageBatch<?> batch) {
            }

            @Override
            public void forceClose() {
                forceCalls.incrementAndGet();
            }

            @Override
            public void close() {
                closeCalls.incrementAndGet();
            }
        };
        StartupBlockingSource incoming = new StartupBlockingSource();
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addBinding(outgoing);
        graph.addIncomingConnector("incoming", incoming);

        assertSame(startupFailure, assertThrows(IllegalStateException.class, graph::start));

        assertEquals(0, incoming.runCalls());
        assertTrue(incoming.forced());
        assertEquals(1, forceCalls.get());
        assertEquals(1, closeCalls.get());
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        graph.close();
    }

    @Test
    void startupWaitersObserveFailureOnlyAfterRollbackCompletes() throws Exception {
        IllegalStateException startupFailure = new IllegalStateException("source is not ready");
        IllegalStateException cleanupFailure = new IllegalStateException("source cleanup failed");
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch readinessEntered = new CountDownLatch(1);
        CountDownLatch releaseReadiness = new CountDownLatch(1);
        CountDownLatch forceStarted = new CountDownLatch(1);
        CountDownLatch releaseForce = new CountDownLatch(1);
        CountDownLatch stop = new CountDownLatch(1);
        AtomicInteger forceCalls = new AtomicInteger();
        IncomingConnector source = new IncomingConnector() {
            @Override
            public void run(IncomingConnectorContext context) {
                running.countDown();
                readinessEntered.countDown();
                await(releaseReadiness);
                throw startupFailure;
            }

            @Override
            public void drain() {
            }

            @Override
            public void forceClose() {
                forceCalls.incrementAndGet();
                forceStarted.countDown();
                await(releaseForce);
                stop.countDown();
                throw cleanupFailure;
            }

            @Override
            public void close() {
                stop.countDown();
            }
        };
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("source", source);

        AsyncTask owner = async(graph::start);
        await(readinessEntered);
        AsyncTask waiter = async(graph::start);
        awaitWaiting(waiter);
        releaseReadiness.countDown();
        await(forceStarted);

        assertFalse(waiter.completion().isDone(), "startup waiter returned before rollback completed");
        releaseForce.countDown();

        assertSame(startupFailure, failure(owner));
        assertSame(startupFailure, failure(waiter));
        assertSame(startupFailure, graph.failure().orElseThrow());
        assertEquals(1, forceCalls.get());
        assertEquals(1, startupFailure.getSuppressed().length);
        assertSame(cleanupFailure, startupFailure.getSuppressed()[0]);
        graph.close();
    }

    @Test
    void concurrentCloseCallersWaitForOneCleanup() throws Exception {
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        AtomicInteger closeCalls = new AtomicInteger();
        Connector binding = new Connector() {
            @Override
            public void forceClose() {
            }

            @Override
            public void close() {
                closeCalls.incrementAndGet();
                closeStarted.countDown();
                await(releaseClose);
            }
        };
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addBinding(binding);
        graph.start();

        AsyncTask owner = async(graph::close);
        await(closeStarted);
        AsyncTask waiter = async(graph::close);
        awaitWaiting(waiter);

        assertFalse(waiter.completion().isDone(), "close waiter returned before connector cleanup completed");
        releaseClose.countDown();
        awaitSuccess(owner);
        awaitSuccess(waiter);

        assertEquals(1, closeCalls.get());
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
    }

    @Test
    void startupReadinessFailureRollsBackInReverseOrderAndMakesGraphTerminal() {
        List<String> events = new CopyOnWriteArrayList<>();
        IllegalStateException startupFailure = new IllegalStateException("second source is not ready");
        ManagedSource first = ManagedSource.running("first", events, new AtomicBoolean(), () -> true);
        ManagedSource second = ManagedSource.readinessFailure("second", events, startupFailure);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("first", first);
        graph.addIncomingConnector("second", second);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, graph::start);

        assertSame(startupFailure, thrown);
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertSame(startupFailure, graph.failure().orElseThrow());
        assertEquals(List.of("force-second", "force-first", "close-second", "close-first"),
                     lifecycleEvents(events));
        assertThrows(IllegalStateException.class, graph::start);
        assertThrows(IllegalStateException.class, graph::ensureRunning);

        graph.close();
        assertEquals(List.of("force-second", "force-first", "close-second", "close-first"),
                     lifecycleEvents(events));
    }

    @Test
    void failureImmediatelyAfterActivationFailsRunningGraph() {
        List<String> events = new CopyOnWriteArrayList<>();
        IllegalStateException startupFailure = new IllegalStateException("second source cannot start admission");
        ManagedSource first = ManagedSource.running("first", events, new AtomicBoolean(), () -> true);
        ManagedSource second = ManagedSource.admissionFailure("second", events, startupFailure);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("first", first);
        graph.addIncomingConnector("second", second);

        graph.start();
        awaitState(graph, DefaultMessagingGraph.State.FAILED);
        awaitCondition(() -> lifecycleEvents(events).size() == 4);

        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertSame(startupFailure, graph.failure().orElseThrow().getCause());
        assertTrue(events.indexOf("admit-first") < events.indexOf("admission-fail-second"), events.toString());
        assertEquals(List.of("force-second", "force-first", "close-second", "close-first"),
                     lifecycleEvents(events));
        assertThrows(IllegalStateException.class, graph::start);
        assertThrows(MessagingException.class, graph::close);
    }

    @Test
    void gracefulDrainAllowsAdmittedNestedDispatchAndRejectsNewTopLevelWork() throws Exception {
        MessagingExecutionConfig config = config(SHUTDOWN_TIMEOUT);
        DeliveryEngine engine = engine(config, "upstream", "downstream");
        DefaultMessagingGraph graph = new DefaultMessagingGraph(engine);
        graph.start();
        CountDownLatch rootStarted = new CountDownLatch(1);
        CountDownLatch allowNested = new CountDownLatch(1);
        CountDownLatch nestedCompleted = new CountDownLatch(1);

        AsyncTask admitted = async(() -> engine.dispatch("upstream",
                                                        MessageBatch.create(List.of(message("root"))),
                                                        () -> {
            rootStarted.countDown();
            await(allowNested);
            engine.dispatch("downstream",
                            MessageBatch.create(List.of(message("nested"))),
                            nestedCompleted::countDown);
        }));
        await(rootStarted);
        AsyncTask closing = async(graph::close);

        MessagingRejectedException rejected = awaitShutdownRejection(engine, "upstream");
        assertEquals(MessagingRejectedException.Reason.SHUTDOWN, rejected.reason());

        allowNested.countDown();
        await(nestedCompleted);
        awaitSuccess(admitted);
        awaitSuccess(closing);
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
    }

    @Test
    void drainTimeoutForcesInterruptionAndClosesBindingsInReverseOrder() throws Exception {
        Duration timeout = Duration.ofMillis(100);
        MessagingExecutionConfig config = config(timeout);
        DeliveryEngine engine = engine(config, "orders");
        DefaultMessagingGraph graph = new DefaultMessagingGraph(engine);
        List<String> events = new CopyOnWriteArrayList<>();
        TrackingBinding first = new TrackingBinding("first", events);
        TrackingBinding second = new TrackingBinding("second", events);
        graph.addBinding(first);
        graph.addBinding(second);
        graph.start();
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        AsyncTask delivery = async(() -> engine.dispatch("orders",
                                                         MessageBatch.create(List.of(message("blocked"))),
                                                         () -> {
            deliveryStarted.countDown();
            try {
                releaseDelivery.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }));
        await(deliveryStarted);
        try {
            MessagingException failure = assertThrows(MessagingException.class, graph::close);

            assertThat(failure.getMessage(), containsString("drain timed out"));
            await(interrupted);
            assertEquals(List.of("force-second", "force-first", "close-second", "close-first"), events);
            assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
            assertThrows(MessagingException.class, graph::close);
        } finally {
            releaseDelivery.countDown();
            awaitCompletion(delivery);
        }
    }

    @Test
    void runtimeSourceFailureFailsGraphAndClosesResourcesInReverseOrder() {
        List<String> events = new CopyOnWriteArrayList<>();
        IllegalStateException runtimeFailure = new IllegalStateException("poll failed");
        TrackingBinding resource = new TrackingBinding("resource", events);
        ManagedSource source = ManagedSource.runtimeFailure("source", events, runtimeFailure);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addBinding(resource);
        graph.addIncomingConnector("source", source);
        graph.start();

        source.fail();
        await(resource.closedSignal());
        awaitState(graph, DefaultMessagingGraph.State.FAILED);

        Throwable graphFailure = graph.failure().orElseThrow();
        assertThat(graphFailure.getMessage(), containsString("source failed"));
        assertSame(runtimeFailure, graphFailure.getCause());
        assertEquals(List.of("force-source", "force-resource", "close-source", "close-resource"),
                     lifecycleEvents(events));
        assertThrows(IllegalStateException.class, graph::ensureRunning);
        assertSame(graphFailure, assertThrows(MessagingException.class, graph::close));
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
    }

    @Test
    void sourceFailureDuringDrainIsReported() {
        IllegalStateException sourceFailure = new IllegalStateException("source stop failed");
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("source", new StopFailingSource(sourceFailure));
        graph.start();

        IllegalStateException failure = assertThrows(IllegalStateException.class, graph::close);

        assertSame(sourceFailure, failure);
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertSame(sourceFailure, graph.failure().orElseThrow());
        assertSame(failure, assertThrows(IllegalStateException.class, graph::close));
    }

    @Test
    void rollbackHandlesOneFailureInstanceFromStartupAndCleanup() {
        IllegalStateException sharedFailure = new IllegalStateException("shared lifecycle failure");
        CountDownLatch running = new CountDownLatch(1);
        IncomingConnector source = new IncomingConnector() {
            @Override
            public void run(IncomingConnectorContext context) {
                running.countDown();
                throw sharedFailure;
            }

            @Override
            public void drain() {
            }

            @Override
            public void forceClose() {
                throw sharedFailure;
            }

            @Override
            public void close() {
                throw sharedFailure;
            }
        };
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("shared-failure", source);

        assertSame(sharedFailure, assertThrows(IllegalStateException.class, graph::start));
        assertEquals(0, sharedFailure.getSuppressed().length);

        graph.close();
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
    }

    @Test
    void startingUpstreamTransitivelyStartsDownstreamStreamInput() {
        List<String> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch streamDelivered = new CountDownLatch(1);
        Message<String> streamMessage = message("from-stream");
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> upstream = builder.channel("upstream", String.class);
        MessagingChannel<String> downstream = builder.channel("downstream", String.class);
        builder.route(upstream, downstream)
                .messageSource(downstream, Stream.of(streamMessage))
                .messageSink(downstream, message -> {
                    delivered.add(message.entity());
                    if (message == streamMessage) {
                        streamDelivered.countDown();
                    }
                });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            await(streamDelivered);
            graph.emitter(upstream).emitMessage(message("from-upstream"));

            assertEquals(List.of("from-stream", "from-upstream"), delivered);
        }
    }

    @Test
    void closeCancelsSourceStartupWithoutWaitingForTheStartupDeadline() throws Exception {
        DefaultMessagingGraph graph = graph(config(Duration.ofSeconds(2)));
        StartupBlockingSource source = new StartupBlockingSource();
        graph.addIncomingConnector("blocked", source);
        AsyncTask startup = async(graph::start);
        await(source.running());
        awaitState(graph, DefaultMessagingGraph.State.STARTING);

        long started = System.nanoTime();
        graph.close();
        long elapsed = System.nanoTime() - started;

        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "Close did not cancel startup promptly");
        assertEquals(DefaultMessagingGraph.State.CLOSED, graph.state());
        assertTrue(source.forced());
        assertThrows(ExecutionException.class,
                     () -> startup.completion().get(WAIT.toNanos(), TimeUnit.NANOSECONDS));
    }

    @Test
    void normalManagedSourceTerminationFailsRunningGraph() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        NormalEndingSource source = new NormalEndingSource();
        graph.addIncomingConnector("ending", source);

        graph.start();
        awaitState(graph, DefaultMessagingGraph.State.FAILED);

        Throwable graphFailure = graph.failure().orElseThrow();
        assertThat(graphFailure.getMessage(), containsString("ending failed"));
        assertThrows(IllegalStateException.class, graph::ensureRunning);
        assertSame(graphFailure, assertThrows(MessagingException.class, graph::close));
    }

    @Test
    void forcedCleanupDefersCloseUntilTimedOutForceReturns() throws Exception {
        Duration timeout = Duration.ofMillis(50);
        DefaultMessagingGraph graph = graph(config(timeout));
        CountDownLatch forceStarted = new CountDownLatch(1);
        CountDownLatch releaseForce = new CountDownLatch(1);
        CountDownLatch forceFinished = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        AtomicBoolean forced = new AtomicBoolean();
        AtomicBoolean closeBeforeForce = new AtomicBoolean();
        AtomicBoolean closeInterrupted = new AtomicBoolean();
        List<String> events = new CopyOnWriteArrayList<>();
        graph.addBinding(new Connector() {
            @Override
            public void forceClose() {
                events.add("force-start");
                forceStarted.countDown();
                awaitUninterruptibly(releaseForce);
                forced.set(true);
                events.add("force-end");
                forceFinished.countDown();
            }

            @Override
            public void close() {
                closeBeforeForce.set(!forced.get());
                closeInterrupted.set(Thread.currentThread().isInterrupted());
                events.add("close");
                closeStarted.countDown();
            }
        });

        long started = System.nanoTime();
        AsyncTask closing = async(graph::close);
        try {
            await(forceStarted);
            Throwable closeFailure = failure(closing);
            long elapsed = System.nanoTime() - started;

            assertTrue(closeFailure instanceof MessagingException, closeFailure.toString());
            assertThat(closeFailure.getMessage(), containsString("Timed out while attempting to force close"));
            assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "Forced cleanup exceeded its absolute deadline");
            assertFalse(forced.get());
            assertEquals(1L, closeStarted.getCount(), "Normal close entered before force close returned");
        } finally {
            releaseForce.countDown();
        }

        await(forceFinished);
        await(closeStarted);
        assertFalse(closeBeforeForce.get());
        assertFalse(closeInterrupted.get(), "Deferred close inherited the force timeout interruption");
        assertEquals(List.of("force-start", "force-end", "close"), events);
    }

    @Test
    void forcedCleanupDoesNotInterruptCloseWhenDeadlineExpires() throws Exception {
        Duration timeout = Duration.ofMillis(50);
        DefaultMessagingGraph graph = graph(config(timeout));
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        AtomicBoolean closeInterrupted = new AtomicBoolean();
        graph.addBinding(new Connector() {
            @Override
            public void forceClose() {
            }

            @Override
            public void close() {
                closeStarted.countDown();
                awaitUninterruptibly(releaseClose);
                closeInterrupted.set(Thread.currentThread().isInterrupted());
                closeFinished.countDown();
            }
        });

        AsyncTask closing = async(graph::close);
        try {
            await(closeStarted);

            Throwable closeFailure = failure(closing);

            assertTrue(closeFailure instanceof MessagingException, closeFailure.toString());
            assertThat(closeFailure.getMessage(), containsString("Timed out while attempting to close"));
        } finally {
            releaseClose.countDown();
        }

        await(closeFinished);
        assertFalse(closeInterrupted.get(), "Post-force close inherited the lifecycle timeout interruption");
    }

    @Test
    void connectorCloseIsBoundedByOneCleanupDeadline() {
        Duration timeout = Duration.ofMillis(50);
        DefaultMessagingGraph graph = graph(config(timeout));
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        AtomicBoolean forceRequested = new AtomicBoolean();
        graph.addBinding(new Connector() {
            @Override
            public void forceClose() {
                forceRequested.set(true);
                releaseClose.countDown();
            }

            @Override
            public void close() {
                closeStarted.countDown();
                try {
                    releaseClose.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        graph.start();

        long started = System.nanoTime();
        MessagingException failure = assertThrows(MessagingException.class, graph::close);
        long elapsed = System.nanoTime() - started;

        await(closeStarted);
        assertThat(failure.getMessage(), containsString("Timed out while attempting to close connector binding"));
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "Connector close exceeded the bounded cleanup phase");
        assertTrue(forceRequested.get());
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertThrows(MessagingException.class, graph::close);
    }

    @Test
    void graphRejectsReusedSourceAndManagedBindingIdentities() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        ManagedSource source = ManagedSource.running("source", new CopyOnWriteArrayList<>(),
                                                     new AtomicBoolean(), () -> true);
        TrackingBinding binding = new TrackingBinding("sink", new CopyOnWriteArrayList<>());

        graph.addIncomingConnector("first", source);
        graph.addBinding(binding);

        assertThrows(IllegalArgumentException.class, () -> graph.addIncomingConnector("second", source));
        assertThrows(IllegalArgumentException.class, () -> graph.addBinding(binding));
        graph.close();
    }

    @Test
    void rejectsUnknownRouteBeforePreparingSources() {
        List<String> events = new CopyOnWriteArrayList<>();
        ManagedSource source = ManagedSource.running("source", events, new AtomicBoolean(), () -> true);
        MessagingExecutionConfig config = config(SHUTDOWN_TIMEOUT);
        DefaultMessagingGraph graph = graph(config);
        graph.addChannel("known", new NoOpChannel(), config);
        graph.addIncomingConnector("source", source);
        graph.addRoute("known", "missing");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, graph::prepare);

        assertThat(failure.getMessage(), containsString("Unknown messaging route target missing"));
        assertFalse(source.prepared());
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertEquals(List.of("force-source", "close-source"), lifecycleEvents(events));
    }

    @Test
    void rejectsCycleBeforePreparingSources() {
        List<String> events = new CopyOnWriteArrayList<>();
        ManagedSource source = ManagedSource.running("source", events, new AtomicBoolean(), () -> true);
        MessagingExecutionConfig config = config(SHUTDOWN_TIMEOUT);
        DefaultMessagingGraph graph = graph(config);
        graph.addChannel("first", new NoOpChannel(), config);
        graph.addChannel("second", new NoOpChannel(), config);
        graph.addIncomingConnector("source", source);
        graph.addRoute("first", "second");
        graph.addRoute("second", "first");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, graph::prepare);

        assertThat(failure.getMessage(), containsString("first -> second -> first"));
        assertFalse(source.prepared());
        assertEquals(DefaultMessagingGraph.State.FAILED, graph.state());
        assertEquals(List.of("force-source", "close-source"), lifecycleEvents(events));
    }

    private static DefaultMessagingGraph graph(MessagingExecutionConfig config) {
        return new DefaultMessagingGraph(new DeliveryEngine(config));
    }

    private static DeliveryEngine engine(MessagingExecutionConfig config, String... channels) {
        DeliveryEngine engine = new DeliveryEngine(config);
        for (String channel : channels) {
            engine.registerChannel(channel, config);
        }
        return engine;
    }

    private static MessagingExecutionConfig config(Duration shutdownTimeout) {
        return MessagingExecutionConfig.builder()
                .queueCapacity(0)
                .maxInFlightMessages(10)
                .shutdownTimeout(shutdownTimeout)
                .build();
    }

    private static Message<String> message(String value) {
        return Message.create(value);
    }

    private static List<String> lifecycleEvents(List<String> events) {
        return events.stream()
                .filter(event -> event.startsWith("force-") || event.startsWith("close-"))
                .toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(WAIT.toNanos(), TimeUnit.NANOSECONDS), "Timed out waiting for test signal");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test signal", e);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitState(DefaultMessagingGraph graph, DefaultMessagingGraph.State expected) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (graph.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, graph.state());
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for test condition");
    }

    private static void awaitWaiting(AsyncTask task) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        Thread.State state;
        do {
            if (task.completion().isDone()) {
                throw new AssertionError("Task completed instead of waiting");
            }
            state = task.thread().getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Task did not enter a waiting state; last state was " + state);
    }

    private static MessagingRejectedException awaitShutdownRejection(DeliveryEngine engine, String channel) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                engine.dispatch(channel, MessageBatch.create(List.of(message("probe"))), () -> { });
            } catch (MessagingRejectedException e) {
                return e;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Timed out waiting for messaging drain to reject new work");
    }

    private static AsyncTask async(Runnable runnable) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                runnable.run();
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
        return new AsyncTask(thread, completion);
    }

    private static void awaitSuccess(AsyncTask task)
            throws ExecutionException, InterruptedException, TimeoutException {
        task.completion().get(WAIT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void awaitCompletion(AsyncTask task) throws InterruptedException, TimeoutException {
        try {
            awaitSuccess(task);
        } catch (ExecutionException ignored) {
            // Forced shutdown is expected to reject the interrupted delivery.
        }
    }

    private static Throwable failure(AsyncTask task)
            throws InterruptedException, TimeoutException {
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> task.completion().get(WAIT.toNanos(), TimeUnit.NANOSECONDS));
        return exception.getCause();
    }

    private record AsyncTask(Thread thread, CompletableFuture<Void> completion) {
    }

    private static final class NoOpChannel implements MessagingChannel<Object> {
        @Override
        public String name() {
            return "no-op";
        }

        @Override
        public GenericType<Object> payloadType() {
            return GenericType.OBJECT;
        }
    }

    private static final class DualConnector implements IncomingConnector, OutgoingConnector {
        private final List<String> events;
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);

        private DualConnector(List<String> events) {
            this.events = events;
        }

        @Override
        public void run(IncomingConnectorContext context) {
            events.add("incoming-run");
            running.countDown();
            events.add("incoming-ready");
            if (!context.awaitRunning()) {
                return;
            }
            events.add("incoming-admit");
            await(stopped);
            events.add("incoming-checkpoint");
        }

        @Override
        public void drain() {
            events.add("incoming-stop");
            stopped.countDown();
        }

        @Override
        public void start() {
            events.add("outgoing-start");
        }

        @Override
        public void sendBatch(MessageBatch<?> batch) {
        }

        @Override
        public void forceClose() {
            events.add("force-close");
            stopped.countDown();
        }

        @Override
        public void close() {
            events.add("close");
            stopped.countDown();
        }
    }

    private static class TrackingBinding implements Connector {
        private final String name;
        private final List<String> events;
        private final CountDownLatch closedSignal = new CountDownLatch(1);
        private final AtomicBoolean forced = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private TrackingBinding(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void forceClose() {
            if (forced.compareAndSet(false, true)) {
                events.add("force-" + name);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                events.add("close-" + name);
                closedSignal.countDown();
            }
        }

        private CountDownLatch closedSignal() {
            return closedSignal;
        }
    }

    private static final class ManagedSource extends TrackingBinding implements IncomingConnector {
        private final AtomicBoolean ready;
        private final BooleanSupplier admissionGuard;
        private final RuntimeException readinessFailure;
        private final RuntimeException admissionFailure;
        private final RuntimeException runtimeFailure;
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch admission = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);
        private final CountDownLatch fail = new CountDownLatch(1);
        private final AtomicBoolean prepared = new AtomicBoolean();

        private ManagedSource(String name,
                              List<String> events,
                              AtomicBoolean ready,
                              BooleanSupplier admissionGuard,
                              RuntimeException readinessFailure,
                              RuntimeException admissionFailure,
                              RuntimeException runtimeFailure) {
            super(name, events);
            this.ready = ready;
            this.admissionGuard = admissionGuard;
            this.readinessFailure = readinessFailure;
            this.admissionFailure = admissionFailure;
            this.runtimeFailure = runtimeFailure;
        }

        private static ManagedSource running(String name,
                                             List<String> events,
                                             AtomicBoolean ready,
                                             BooleanSupplier admissionGuard) {
            return new ManagedSource(name, events, ready, admissionGuard, null, null, null);
        }

        private static ManagedSource readinessFailure(String name,
                                                      List<String> events,
                                                      RuntimeException failure) {
            return new ManagedSource(name, events, new AtomicBoolean(), () -> true, failure, null, null);
        }

        private static ManagedSource admissionFailure(String name,
                                                      List<String> events,
                                                      RuntimeException failure) {
            return new ManagedSource(name, events, new AtomicBoolean(), () -> true, null, failure, null);
        }

        private static ManagedSource runtimeFailure(String name,
                                                    List<String> events,
                                                    RuntimeException failure) {
            return new ManagedSource(name, events, new AtomicBoolean(), () -> true, null, null, failure);
        }

        @Override
        public void run(IncomingConnectorContext context) {
            prepared.set(true);
            events().add("prepare-" + name());
            running.countDown();
            if (!prepared.get()) {
                throw new AssertionError("Source readiness was checked before graph preparation");
            }
            if (readinessFailure != null) {
                throw readinessFailure;
            }
            ready.set(true);
            events().add("ready-" + name());
            if (!context.awaitRunning()) {
                return;
            }
            if (!admissionGuard.getAsBoolean()) {
                throw new AssertionError("Source admission started before every source was ready");
            }
            if (admissionFailure != null) {
                events().add("admission-fail-" + name());
                throw admissionFailure;
            }
            events().add("admit-" + name());
            admission.countDown();
            if (runtimeFailure != null) {
                MessagingGraphTest.await(fail);
                throw runtimeFailure;
            }
            MessagingGraphTest.await(stop);
        }

        @Override
        public void drain() {
            events().add("stop-" + name());
            admission.countDown();
            stop.countDown();
        }

        @Override
        public void forceClose() {
            super.forceClose();
            admission.countDown();
            stop.countDown();
            fail.countDown();
        }

        @Override
        public void close() {
            super.close();
            admission.countDown();
            stop.countDown();
            fail.countDown();
        }

        private boolean prepared() {
            return prepared.get();
        }

        private void fail() {
            fail.countDown();
        }

        private String name() {
            return super.name;
        }

        private List<String> events() {
            return super.events;
        }

        private static void await(CountDownLatch latch, Duration timeout) {
            try {
                if (!latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw new MessagingException("Timed out waiting for managed test source");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingException("Interrupted while waiting for managed test source", e);
            }
        }
    }

    private static final class StartupBlockingSource implements IncomingConnector {
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch startupReleased = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);
        private final AtomicBoolean forced = new AtomicBoolean();
        private final AtomicInteger runCalls = new AtomicInteger();

        @Override
        public void run(IncomingConnectorContext context) {
            runCalls.incrementAndGet();
            running.countDown();
            try {
                if (!startupReleased.await(WAIT.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw new MessagingException("Test source startup timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingException("Test source startup was interrupted", e);
            }
            if (context.awaitRunning()) {
                await(stopped);
            }
        }

        @Override
        public void drain() {
            stopped.countDown();
        }

        @Override
        public void forceClose() {
            forced.set(true);
            startupReleased.countDown();
            stopped.countDown();
        }

        @Override
        public void close() {
            forceClose();
        }

        private CountDownLatch running() {
            return running;
        }

        private boolean forced() {
            return forced.get();
        }

        private void releaseStartup() {
            startupReleased.countDown();
        }

        private int runCalls() {
            return runCalls.get();
        }
    }

    private static final class NormalEndingSource implements IncomingConnector {
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch admission = new CountDownLatch(1);
        private final AtomicReference<Thread> owner = new AtomicReference<>();

        @Override
        public void run(IncomingConnectorContext context) {
            owner.set(Thread.currentThread());
            running.countDown();
            if (context.awaitRunning()) {
                admission.countDown();
            }
        }

        @Override
        public void drain() {
            admission.countDown();
        }

        @Override
        public void forceClose() {
            admission.countDown();
        }

        @Override
        public void close() {
            admission.countDown();
        }
    }

    private static final class StopFailingSource implements IncomingConnector {
        private final RuntimeException failure;
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch admission = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);

        private StopFailingSource(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void run(IncomingConnectorContext context) {
            running.countDown();
            if (context.awaitRunning()) {
                admission.countDown();
                await(stop);
                throw failure;
            }
        }

        @Override
        public void drain() {
            stop.countDown();
        }

        @Override
        public void forceClose() {
            admission.countDown();
            stop.countDown();
        }

        @Override
        public void close() {
            forceClose();
        }
    }
}
