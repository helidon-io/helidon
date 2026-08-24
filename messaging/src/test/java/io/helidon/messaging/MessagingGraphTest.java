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
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThat(graph.state(), is(DefaultMessagingGraph.State.RUNNING));
        assertThat(first.prepared(), is(true));
        assertThat(second.prepared(), is(true));
        int lastReady = Math.max(events.indexOf("ready-first"), events.indexOf("ready-second"));
        assertThat(events.toString(), lastReady < events.indexOf("admit-first"), is(true));
        assertThat(events.toString(), lastReady < events.indexOf("admit-second"), is(true));

        graph.close();
        graph.close();
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(lifecycleEvents(events), is(List.of("close-second", "close-first")));
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

        assertThat(List.copyOf(events),
                   is(List.of("start-outgoing",
                              "run-incoming",
                              "ready-incoming",
                              "admit-incoming")));

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

        assertThat("Incoming connector checkpointed before runtime drain",
                   events.contains("checkpoint-incoming"),
                   is(false));

        releaseDelivery.countDown();
        awaitSuccess(delivery);
        awaitSuccess(closing);

        assertThat(events,
                   is(List.of("start-outgoing",
                              "run-incoming",
                              "ready-incoming",
                              "admit-incoming",
                              "delivery-start",
                              "stop-incoming",
                              "delivery-end",
                              "checkpoint-incoming",
                              "close-incoming",
                              "close-outgoing")));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
    }

    @Test
    void dualConnectorRegisteredAsOutgoingUsesOnlyOutgoingLifecycle() {
        List<String> events = new CopyOnWriteArrayList<>();
        DualConnector connector = new DualConnector(events);
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addBinding(connector);

        graph.start();
        graph.close();

        assertThat(events, is(List.of("outgoing-start", "close")));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
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

        assertThat(events,
                   is(List.of("incoming-run",
                              "incoming-ready",
                              "incoming-admit",
                              "incoming-stop",
                              "incoming-checkpoint",
                              "close")));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
    }

    @Test
    void forcedCleanupOfDualConnectorUsesItsRegistrationRole() {
        List<String> outgoingEvents = new CopyOnWriteArrayList<>();
        DualConnector outgoing = new DualConnector(outgoingEvents);
        DefaultMessagingGraph outgoingGraph = graph(config(SHUTDOWN_TIMEOUT));
        outgoingGraph.addBinding(outgoing);

        outgoingGraph.close();

        assertThat(outgoingEvents, is(List.of("force-close", "close")));

        List<String> incomingEvents = new CopyOnWriteArrayList<>();
        DualConnector incoming = new DualConnector(incomingEvents);
        DefaultMessagingGraph incomingGraph = graph(config(SHUTDOWN_TIMEOUT));
        incomingGraph.addIncomingConnector("dual", incoming);

        incomingGraph.close();

        assertThat(incomingEvents, is(List.of("force-close", "close")));
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

        assertThat(graph.state(), is(DefaultMessagingGraph.State.RUNNING));
        assertThat(source.runCalls(), is(1));
        graph.close();
    }

    @Test
    void shutdownTimeoutDoesNotBoundOutgoingStartup() throws Exception {
        Duration shutdownTimeout = Duration.ofMillis(250);
        StartupBlockingOutgoing outgoing = new StartupBlockingOutgoing();
        DefaultMessagingGraph graph = graph(config(shutdownTimeout));
        graph.addBinding(outgoing);

        AsyncTask startup = async(graph::start);
        try {
            await(outgoing.starting());
            assertPendingFor(startup, shutdownTimeout.multipliedBy(2));
        } finally {
            outgoing.releaseStartup();
        }
        try {
            awaitSuccess(startup);
            assertThat(graph.state(), is(DefaultMessagingGraph.State.RUNNING));
            assertThat(outgoing.interrupted(), is(false));
        } finally {
            closeIfNotTerminal(graph);
        }
    }

    @Test
    void sequentialOutgoingStartsDoNotShareShutdownTimeout() throws Exception {
        Duration shutdownTimeout = Duration.ofMillis(500);
        Duration startupHold = Duration.ofMillis(300);
        StartupBlockingOutgoing first = new StartupBlockingOutgoing();
        StartupBlockingOutgoing second = new StartupBlockingOutgoing();
        DefaultMessagingGraph graph = graph(config(shutdownTimeout));
        graph.addBinding(first);
        graph.addBinding(second);

        AsyncTask startup = async(graph::start);
        try {
            await(first.starting());
            assertPendingFor(startup, startupHold);
            first.releaseStartup();
            await(second.starting());
            assertPendingFor(startup, startupHold);
        } finally {
            first.releaseStartup();
            second.releaseStartup();
        }
        try {
            awaitSuccess(startup);
            assertThat(graph.state(), is(DefaultMessagingGraph.State.RUNNING));
            assertThat(first.interrupted(), is(false));
            assertThat(second.interrupted(), is(false));
        } finally {
            closeIfNotTerminal(graph);
        }
    }

    @Test
    void shutdownTimeoutDoesNotBoundIncomingReadiness() throws Exception {
        Duration shutdownTimeout = Duration.ofMillis(250);
        StartupBlockingSource source = new StartupBlockingSource();
        DefaultMessagingGraph graph = graph(config(shutdownTimeout));
        graph.addIncomingConnector("source", source);

        AsyncTask startup = async(graph::start);
        try {
            await(source.running());
            assertPendingFor(startup, shutdownTimeout.multipliedBy(2));
        } finally {
            source.releaseStartup();
        }
        try {
            awaitSuccess(startup);
            assertThat(graph.state(), is(DefaultMessagingGraph.State.RUNNING));
        } finally {
            closeIfNotTerminal(graph);
        }
    }

    @Test
    void delayedOutgoingStartupFailureUsesFreshRollbackDeadline() throws Exception {
        Duration shutdownTimeout = Duration.ofSeconds(1);
        IllegalStateException startupFailure = new IllegalStateException("outgoing is not ready");
        CountDownLatch releaseForce = new CountDownLatch(1);
        StartupBlockingOutgoing outgoing = new StartupBlockingOutgoing(startupFailure, releaseForce);
        DefaultMessagingGraph graph = graph(config(shutdownTimeout));
        graph.addBinding(outgoing);

        AsyncTask startup = async(graph::start);
        try {
            await(outgoing.starting());
            assertPendingFor(startup, shutdownTimeout.multipliedBy(2));
            outgoing.releaseStartup();
            await(outgoing.forceStarted());
            assertPendingFor(startup, Duration.ofMillis(100));
        } finally {
            outgoing.releaseStartup();
            releaseForce.countDown();
        }

        assertThat(failure(startup), sameInstance(startupFailure));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(outgoing.forceCalls(), is(1));
        assertThat(outgoing.closeCalls(), is(1));
        graph.close();
        assertThat(outgoing.forceCalls(), is(1));
        assertThat(outgoing.closeCalls(), is(1));
    }

    @Test
    void closeCancelsBlockedOutgoingStartupPromptly() throws Exception {
        StartupBlockingOutgoing outgoing = new StartupBlockingOutgoing();
        DefaultMessagingGraph graph = graph(config(Duration.ofSeconds(30)));
        graph.addBinding(outgoing);
        AsyncTask startup = async(graph::start);

        try {
            await(outgoing.starting());
            awaitState(graph, DefaultMessagingGraph.State.STARTING);
            AsyncTask closing = async(graph::close);
            await(outgoing.forceStarted());
            await(outgoing.startExited());
            awaitSuccess(closing);
        } finally {
            outgoing.releaseStartup();
        }

        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(outgoing.forced(), is(true));
        assertThrows(ExecutionException.class,
                     () -> startup.completion().get(WAIT.toNanos(), TimeUnit.NANOSECONDS));
    }

    @Test
    void interruptedOutgoingStartupRollsBackBeforeRestoringInterrupt() throws Exception {
        StartupBlockingOutgoing outgoing = new StartupBlockingOutgoing();
        DefaultMessagingGraph graph = graph(config(Duration.ofSeconds(30)));
        graph.addBinding(outgoing);
        AtomicBoolean interruptedAtExit = new AtomicBoolean();
        AsyncTask startup = async(() -> {
            try {
                graph.start();
            } finally {
                interruptedAtExit.set(Thread.currentThread().isInterrupted());
            }
        });

        try {
            await(outgoing.starting());
            startup.thread().interrupt();
            await(outgoing.startExited());
            await(outgoing.forceStarted());
        } finally {
            outgoing.releaseStartup();
        }
        Throwable startupFailure = failure(startup);
        assertThat(startupFailure, instanceOf(MessagingException.class));
        assertThat(startupFailure.getCause(), instanceOf(InterruptedException.class));
        assertThat(interruptedAtExit.get(), is(true));
        assertThat(outgoing.interrupted(), is(true));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(outgoing.forceCalls(), is(1));
        assertThat(outgoing.closeCalls(), is(1));
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

        assertThat(assertThrows(IllegalStateException.class, graph::start), sameInstance(startupFailure));

        assertThat(incoming.runCalls(), is(0));
        assertThat(incoming.forced(), is(true));
        assertThat(forceCalls.get(), is(1));
        assertThat(closeCalls.get(), is(1));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
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

        assertThat("startup waiter returned before rollback completed", waiter.completion().isDone(), is(false));
        releaseForce.countDown();

        assertThat(failure(owner), sameInstance(startupFailure));
        assertThat(failure(waiter), sameInstance(startupFailure));
        assertThat(graph.failure().orElseThrow(), sameInstance(startupFailure));
        assertThat(forceCalls.get(), is(1));
        assertThat(startupFailure.getSuppressed().length, is(1));
        assertThat(startupFailure.getSuppressed()[0], sameInstance(cleanupFailure));
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

        assertThat("close waiter returned before connector cleanup completed", waiter.completion().isDone(), is(false));
        releaseClose.countDown();
        awaitSuccess(owner);
        awaitSuccess(waiter);

        assertThat(closeCalls.get(), is(1));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
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

        assertThat(thrown, sameInstance(startupFailure));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(graph.failure().orElseThrow(), sameInstance(startupFailure));
        assertThat(lifecycleEvents(events),
                   is(List.of("force-second", "force-first", "close-second", "close-first")));
        assertThrows(IllegalStateException.class, graph::start);
        assertThrows(IllegalStateException.class, graph::ensureRunning);

        graph.close();
        assertThat(lifecycleEvents(events),
                   is(List.of("force-second", "force-first", "close-second", "close-first")));
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

        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(graph.failure().orElseThrow().getCause(), sameInstance(startupFailure));
        assertThat(events.toString(),
                   events.indexOf("admit-first") < events.indexOf("admission-fail-second"),
                   is(true));
        assertThat(lifecycleEvents(events),
                   is(List.of("force-second", "force-first", "close-second", "close-first")));
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
        assertThat(rejected.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));

        allowNested.countDown();
        await(nestedCompleted);
        awaitSuccess(admitted);
        awaitSuccess(closing);
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
    }

    @Test
    void closeFromSinkHandsShutdownOffUntilTheCurrentDeliveryCompletes() throws Exception {
        CountDownLatch closeReturned = new CountDownLatch(1);
        CountDownLatch releaseSink = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicReference<MessagingGraph> graphReference = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder().executionConfig(config(SHUTDOWN_TIMEOUT));
        MessagingChannel<String> channel = builder.channel("orders", String.class);
        builder.payloadSink(channel, ignored -> {
            try {
                graphReference.get().close();
                graphReference.get().close();
            } catch (Throwable throwable) {
                closeFailure.set(throwable);
            } finally {
                closeReturned.countDown();
            }
            await(releaseSink);
        });
        MessagingGraph graph = builder.build();
        graphReference.set(graph);
        graph.start();

        AsyncTask delivery = async(() -> graph.emitter(channel).emit("order"));
        await(closeReturned);
        AsyncTask shutdownWaiter = async(graph::close);
        try {
            assertThat(closeFailure.get(), nullValue());
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.DRAINING));
            awaitWaiting(shutdownWaiter);
            assertThat("Delivery completed before its sink returned", delivery.completion().isDone(), is(false));
        } finally {
            releaseSink.countDown();
        }

        awaitSuccess(delivery);
        awaitSuccess(shutdownWaiter);
        assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(((DefaultMessagingGraph) graph).failure().isEmpty(), is(true));
    }

    @Test
    void closeHandsShutdownOffFromAChildDeliveryInAnotherEngine() throws Exception {
        MessagingExecutionConfig config = config(SHUTDOWN_TIMEOUT);
        DeliveryEngine parentEngine = engine(config, "parent");
        DeliveryEngine childEngine = engine(config, "child");
        DefaultMessagingGraph parentGraph = new DefaultMessagingGraph(parentEngine);
        DefaultMessagingGraph childGraph = new DefaultMessagingGraph(childEngine);
        parentGraph.start();
        childGraph.start();
        CountDownLatch closeReturned = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();

        AsyncTask delivery = async(() -> parentEngine.dispatch(
                "parent",
                MessageBatch.create(List.of(message("parent"))),
                () -> childEngine.dispatch(
                        "child",
                        MessageBatch.create(List.of(message("child"))),
                        () -> {
                            try {
                                parentGraph.close();
                            } catch (Throwable throwable) {
                                closeFailure.set(throwable);
                            } finally {
                                closeReturned.countDown();
                            }
                            await(releaseChild);
                        })));
        await(closeReturned);
        AsyncTask shutdownWaiter = async(parentGraph::close);
        try {
            assertThat(closeFailure.get(), nullValue());
            assertThat(parentGraph.state(), is(DefaultMessagingGraph.State.DRAINING));
            awaitWaiting(shutdownWaiter);
        } finally {
            releaseChild.countDown();
        }

        awaitSuccess(delivery);
        awaitSuccess(shutdownWaiter);
        assertThat(parentGraph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(parentGraph.failure().isEmpty(), is(true));
        childGraph.close();
    }

    @Test
    void closeFromSourceHandsShutdownOffUntilTheSourceReturns() throws Exception {
        CountDownLatch closeReturned = new CountDownLatch(1);
        CountDownLatch drainRequested = new CountDownLatch(1);
        CountDownLatch releaseSource = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicReference<DefaultMessagingGraph> graphReference = new AtomicReference<>();
        IncomingConnector source = new IncomingConnector() {
            @Override
            public void run(IncomingConnectorContext context) {
                if (!context.awaitRunning()) {
                    return;
                }
                try {
                    graphReference.get().close();
                    graphReference.get().close();
                } catch (Throwable throwable) {
                    closeFailure.set(throwable);
                } finally {
                    closeReturned.countDown();
                }
                await(releaseSource);
            }

            @Override
            public void drain() {
                drainRequested.countDown();
            }

            @Override
            public void forceClose() {
                releaseSource.countDown();
            }

            @Override
            public void close() {
            }
        };
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graphReference.set(graph);
        graph.addIncomingConnector("source", source);
        graph.start();

        await(closeReturned);
        await(drainRequested);
        AsyncTask shutdownWaiter = async(graph::close);
        try {
            assertThat(closeFailure.get(), nullValue());
            assertThat(graph.state(), is(DefaultMessagingGraph.State.DRAINING));
            awaitWaiting(shutdownWaiter);
        } finally {
            releaseSource.countDown();
        }

        awaitSuccess(shutdownWaiter);
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(graph.failure().isEmpty(), is(true));
    }

    @Test
    void connectorCloseCanReenterGraphClose() {
        AtomicInteger connectorCloseCalls = new AtomicInteger();
        AtomicReference<DefaultMessagingGraph> graphReference = new AtomicReference<>();
        Connector connector = new Connector() {
            @Override
            public void forceClose() {
            }

            @Override
            public void close() {
                connectorCloseCalls.incrementAndGet();
                graphReference.get().close();
                graphReference.get().close();
            }
        };
        DefaultMessagingGraph graph = graph(config(Duration.ofMillis(100)));
        graphReference.set(graph);
        graph.addBinding(connector);
        graph.start();

        graph.close();

        assertThat(connectorCloseCalls.get(), is(1));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(graph.failure().isEmpty(), is(true));
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
            assertThat(events, is(List.of("force-second", "force-first", "close-second", "close-first")));
            assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
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
        assertThat(graphFailure.getCause(), sameInstance(runtimeFailure));
        assertThat(graphFailure.getSuppressed().length, is(0));
        assertThat(lifecycleEvents(events),
                   is(List.of("force-source", "force-resource", "close-source", "close-resource")));
        assertThrows(IllegalStateException.class, graph::ensureRunning);
        assertThat(assertThrows(MessagingException.class, graph::close), sameInstance(graphFailure));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
    }

    @Test
    void sourceFailureDuringDrainIsReported() {
        IllegalStateException sourceFailure = new IllegalStateException("source stop failed");
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        graph.addIncomingConnector("source", new StopFailingSource(sourceFailure));
        graph.start();

        IllegalStateException failure = assertThrows(IllegalStateException.class, graph::close);

        assertThat(failure, sameInstance(sourceFailure));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0].getMessage(), containsString("forced shutdown was requested"));
        assertThat(failure.getSuppressed()[0].getMessage().contains("timed out"), is(false));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(graph.failure().orElseThrow(), sameInstance(sourceFailure));
        assertThat(assertThrows(IllegalStateException.class, graph::close), sameInstance(failure));
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

        assertThat(assertThrows(IllegalStateException.class, graph::start), sameInstance(sharedFailure));
        assertThat(sharedFailure.getSuppressed().length, is(0));

        graph.close();
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
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

            assertThat(delivered, is(List.of("from-stream", "from-upstream")));
        }
    }

    @Test
    void closeCancelsSourceStartupPromptly() throws Exception {
        DefaultMessagingGraph graph = graph(config(Duration.ofSeconds(30)));
        StartupBlockingSource source = new StartupBlockingSource();
        graph.addIncomingConnector("blocked", source);
        AsyncTask startup = async(graph::start);

        try {
            await(source.running());
            awaitState(graph, DefaultMessagingGraph.State.STARTING);
            AsyncTask closing = async(graph::close);
            await(source.forceStarted());
            await(source.runExited());
            awaitSuccess(closing);
        } finally {
            source.releaseStartup();
        }

        assertThat(graph.state(), is(DefaultMessagingGraph.State.CLOSED));
        assertThat(source.forced(), is(true));
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
        assertThat(assertThrows(MessagingException.class, graph::close), sameInstance(graphFailure));
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

            assertThat(closeFailure.toString(), closeFailure, instanceOf(MessagingException.class));
            assertThat(closeFailure.getMessage(), containsString("Timed out while attempting to force close"));
            assertThat("Forced cleanup exceeded its absolute deadline",
                       elapsed < TimeUnit.SECONDS.toNanos(1),
                       is(true));
            assertThat(forced.get(), is(false));
            assertThat("Normal close entered before force close returned", closeStarted.getCount(), is(1L));
        } finally {
            releaseForce.countDown();
        }

        await(forceFinished);
        await(closeStarted);
        assertThat(closeBeforeForce.get(), is(false));
        assertThat("Deferred close inherited the force timeout interruption", closeInterrupted.get(), is(false));
        assertThat(events, is(List.of("force-start", "force-end", "close")));
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

            assertThat(closeFailure.toString(), closeFailure, instanceOf(MessagingException.class));
            assertThat(closeFailure.getMessage(), containsString("Timed out while attempting to close"));
        } finally {
            releaseClose.countDown();
        }

        await(closeFinished);
        assertThat("Post-force close inherited the lifecycle timeout interruption", closeInterrupted.get(), is(false));
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
        assertThat("Connector close exceeded the bounded cleanup phase",
                   elapsed < TimeUnit.SECONDS.toNanos(1),
                   is(true));
        assertThat(forceRequested.get(), is(true));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
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
        assertThat(source.prepared(), is(false));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(lifecycleEvents(events), is(List.of("force-source", "close-source")));
    }

    @Test
    void routedDeliveryLimitUsesTargetInFlightRatherThanTargetPendingCapacity() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        addChannel(graph, "source", 8, 8);
        addChannel(graph, "target", 1, 4);
        graph.addRoute("source", "target");
        graph.prepare();
        try {
            assertThat(graph.maxDeliveryMessages("source"), is(4));
            assertThat(graph.maxDeliveryMessages("target"), is(1));
        } finally {
            graph.close();
        }
    }

    @Test
    void routedDeliveryLimitIncludesTransitiveTargets() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        addChannel(graph, "source", 8, 8);
        addChannel(graph, "middle", 6, 6);
        addChannel(graph, "target", 2, 2);
        graph.addRoute("source", "middle");
        graph.addRoute("middle", "target");
        graph.prepare();
        try {
            assertThat(graph.maxDeliveryMessages("source"), is(2));
            assertThat(graph.maxDeliveryMessages("middle"), is(2));
            assertThat(graph.maxDeliveryMessages("target"), is(2));
        } finally {
            graph.close();
        }
    }

    @Test
    void routedDeliveryLimitUsesSmallestFanOutBranch() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        addChannel(graph, "source", 8, 8);
        addChannel(graph, "left", 4, 4);
        addChannel(graph, "middle", 2, 2);
        addChannel(graph, "right", 3, 3);
        graph.addRoute("source", "left");
        graph.addRoute("source", "middle");
        graph.addRoute("source", "right");
        graph.prepare();
        try {
            assertThat(graph.maxDeliveryMessages("source"), is(2));
            assertThat(graph.maxDeliveryMessages("left"), is(4));
            assertThat(graph.maxDeliveryMessages("middle"), is(2));
            assertThat(graph.maxDeliveryMessages("right"), is(3));
        } finally {
            graph.close();
        }
    }

    @Test
    void unrelatedChannelDoesNotReduceDeliveryLimit() {
        DefaultMessagingGraph graph = graph(config(SHUTDOWN_TIMEOUT));
        addChannel(graph, "source", 6, 3);
        addChannel(graph, "unrelated", 1, 1);
        graph.prepare();
        try {
            assertThat(graph.maxDeliveryMessages("source"), is(3));
            assertThat(graph.maxDeliveryMessages("unrelated"), is(1));
        } finally {
            graph.close();
        }
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
        assertThat(source.prepared(), is(false));
        assertThat(graph.state(), is(DefaultMessagingGraph.State.FAILED));
        assertThat(lifecycleEvents(events), is(List.of("force-source", "close-source")));
    }

    private static DefaultMessagingGraph graph(MessagingExecutionConfig config) {
        return new DefaultMessagingGraph(new DeliveryEngine(config));
    }

    private static void addChannel(DefaultMessagingGraph graph,
                                   String channel,
                                   int maxPendingMessages,
                                   int maxInFlightMessages) {
        MessagingExecutionConfig config = MessagingExecutionConfig.builder()
                .queueCapacity(0)
                .maxPendingMessages(maxPendingMessages)
                .maxInFlightMessages(maxInFlightMessages)
                .shutdownTimeout(SHUTDOWN_TIMEOUT)
                .build();
        graph.addChannel(channel, new NoOpChannel(), config);
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
            assertThat("Timed out waiting for test signal",
                       latch.await(WAIT.toNanos(), TimeUnit.NANOSECONDS),
                       is(true));
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
        assertThat(graph.state(), is(expected));
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat("Timed out waiting for test condition", condition.getAsBoolean(), is(true));
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

    private static void assertPendingFor(AsyncTask task, Duration timeout) {
        assertThrows(TimeoutException.class,
                     () -> task.completion().get(timeout.toNanos(), TimeUnit.NANOSECONDS));
    }

    private static void closeIfNotTerminal(DefaultMessagingGraph graph) {
        if (graph.state() != DefaultMessagingGraph.State.CLOSED
                && graph.state() != DefaultMessagingGraph.State.FAILED) {
            graph.close();
        }
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
        private final CountDownLatch forceStarted = new CountDownLatch(1);
        private final CountDownLatch runExited = new CountDownLatch(1);
        private final AtomicBoolean forced = new AtomicBoolean();
        private final AtomicInteger runCalls = new AtomicInteger();

        @Override
        public void run(IncomingConnectorContext context) {
            try {
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
            } finally {
                runExited.countDown();
            }
        }

        @Override
        public void drain() {
            stopped.countDown();
        }

        @Override
        public void forceClose() {
            forced.set(true);
            forceStarted.countDown();
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

        private CountDownLatch forceStarted() {
            return forceStarted;
        }

        private CountDownLatch runExited() {
            return runExited;
        }

        private void releaseStartup() {
            startupReleased.countDown();
        }

        private int runCalls() {
            return runCalls.get();
        }
    }

    private static final class StartupBlockingOutgoing implements OutgoingConnector {
        private final CountDownLatch starting = new CountDownLatch(1);
        private final CountDownLatch startupReleased = new CountDownLatch(1);
        private final CountDownLatch startExited = new CountDownLatch(1);
        private final CountDownLatch forceStarted = new CountDownLatch(1);
        private final RuntimeException startupFailure;
        private final CountDownLatch forceReleased;
        private final AtomicInteger forceCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicBoolean interrupted = new AtomicBoolean();

        private StartupBlockingOutgoing() {
            this(null, null);
        }

        private StartupBlockingOutgoing(RuntimeException startupFailure) {
            this(startupFailure, null);
        }

        private StartupBlockingOutgoing(RuntimeException startupFailure, CountDownLatch forceReleased) {
            this.startupFailure = startupFailure;
            this.forceReleased = forceReleased;
        }

        @Override
        public void start() {
            starting.countDown();
            try {
                startupReleased.await();
                if (startupFailure != null) {
                    throw startupFailure;
                }
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new MessagingException("Test outgoing startup was interrupted", e);
            } finally {
                startExited.countDown();
            }
        }

        @Override
        public void sendBatch(MessageBatch<?> batch) {
        }

        @Override
        public void forceClose() {
            forceCalls.incrementAndGet();
            forceStarted.countDown();
            startupReleased.countDown();
            if (forceReleased != null) {
                awaitUninterruptibly(forceReleased);
            }
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            startupReleased.countDown();
        }

        private CountDownLatch starting() {
            return starting;
        }

        private CountDownLatch startExited() {
            return startExited;
        }

        private CountDownLatch forceStarted() {
            return forceStarted;
        }

        private void releaseStartup() {
            startupReleased.countDown();
        }

        private boolean forced() {
            return forceCalls.get() > 0;
        }

        private boolean interrupted() {
            return interrupted.get();
        }

        private int forceCalls() {
            return forceCalls.get();
        }

        private int closeCalls() {
            return closeCalls.get();
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
