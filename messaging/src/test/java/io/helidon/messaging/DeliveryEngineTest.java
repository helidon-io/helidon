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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(value = 10)
class DeliveryEngineTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void dispatchUsesNamedRuntimeVirtualThreadAndCompletesSynchronously() throws Exception {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Thread> runtimeThread = new AtomicReference<>();
            AtomicBoolean outputCompleted = new AtomicBoolean();
            AtomicBoolean callerObservedCompletion = new AtomicBoolean();

            AsyncTask caller = async(() -> {
                dispatch(engine, "orders", List.of(message(1)), () -> {
                    runtimeThread.set(Thread.currentThread());
                    entered.countDown();
                    await(release);
                    outputCompleted.set(true);
                });
                callerObservedCompletion.set(outputCompleted.get());
            });

            await(entered);
            Thread dispatchThread = runtimeThread.get();
            assertThat(dispatchThread.isVirtual(), is(true));
            assertThat(dispatchThread.getName().startsWith("helidon-messaging-dispatch-"), is(true));
            assertThat(caller.completion().isDone(), is(false));

            release.countDown();
            await(caller);
            assertThat(callerObservedCompletion.get(), is(true));
        }
    }

    @Test
    void serializesDeliveriesPerChannelWhileIndependentChannelsOverlap() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(1)
                .maxInFlightMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders", "payments")) {
            CountDownLatch firstOrderStarted = new CountDownLatch(1);
            CountDownLatch secondOrderStarted = new CountDownLatch(1);
            CountDownLatch paymentStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstOrder = new CountDownLatch(1);
            AtomicInteger activeOrders = new AtomicInteger();
            AtomicInteger maximumActiveOrders = new AtomicInteger();

            Runnable firstOrderAction = () -> {
                int current = activeOrders.incrementAndGet();
                maximumActiveOrders.accumulateAndGet(current, Math::max);
                firstOrderStarted.countDown();
                try {
                    await(releaseFirstOrder);
                } finally {
                    activeOrders.decrementAndGet();
                }
            };
            Runnable secondOrderAction = () -> {
                int current = activeOrders.incrementAndGet();
                maximumActiveOrders.accumulateAndGet(current, Math::max);
                activeOrders.decrementAndGet();
                secondOrderStarted.countDown();
            };
            ConnectorDelivery firstOrder = submitConnectorDelivery(engine, "orders",
                                                                    List.of(message(1)),
                                                                    firstOrderAction);
            await(firstOrderStarted);
            ConnectorDelivery secondOrder = submitConnectorDelivery(engine, "orders",
                                                                     List.of(message(1)),
                                                                     secondOrderAction);
            ConnectorDelivery payment = submitConnectorDelivery(engine, "payments",
                                                                 List.of(message(1)),
                                                                 paymentStarted::countDown);

            await(paymentStarted);
            assertThat(secondOrderStarted.getCount(), is(1L));
            assertThat(secondOrder.isDone(), is(false));
            assertThat(maximumActiveOrders.get(), is(1));

            releaseFirstOrder.countDown();
            await(firstOrder);
            await(secondOrder);
            await(payment);
            assertThat(secondOrderStarted.getCount(), is(0L));
            assertThat(maximumActiveOrders.get(), is(1));
        }
    }

    @Test
    void admitsExactMessageLimitAndRejectsOversizedBatchAtomically() {
        MessagingExecutionConfig config = configBuilder()
                .maxInFlightMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            AtomicInteger invocations = new AtomicInteger();
            List<Message<?>> exact = List.of(message(4), message(6));

            dispatch(engine, "orders", exact, invocations::incrementAndGet);

            MessagingRejectedException messageLimit = assertThrows(
                    MessagingRejectedException.class,
                    () -> dispatch(engine, "orders",
                                          List.of(message(1), message(1), message(1)),
                                          invocations::incrementAndGet));
            assertThat(messageLimit.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
            assertThat(invocations.get(), is(1));

            dispatch(engine, "orders", exact, invocations::incrementAndGet);
            assertThat(invocations.get(), is(2));
        }
    }

    @Test
    void zeroQueueCapacityBlocksAdmissionAndHonorsAdmissionTimeout() throws Exception {
        MessagingExecutionConfig blockingConfig = configBuilder()
                .queueCapacity(0)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(blockingConfig, "orders")) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            ConnectorDelivery first = submitConnectorDelivery(engine, "orders",
                                                                      List.of(message(1)),
                                                                      () -> {
                                                                          firstStarted.countDown();
                                                                          await(releaseFirst);
                                                                      });
            await(firstStarted);

            AsyncTask second = async(() -> dispatch(engine, "orders",
                                                           List.of(message(1)),
                                                           secondStarted::countDown));
            awaitWaiting(second);
            assertThat(second.completion().isDone(), is(false));
            assertThat(secondStarted.getCount(), is(1L));

            releaseFirst.countDown();
            await(first);
            await(second);
            assertThat(secondStarted.getCount(), is(0L));
        }

        MessagingExecutionConfig timeoutConfig = configBuilder()
                .queueCapacity(0)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(50))
                .build();
        try (DeliveryEngine engine = engine(timeoutConfig, "orders")) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            ConnectorDelivery first = submitConnectorDelivery(engine, "orders",
                                                                      List.of(message(1)),
                                                                      () -> {
                                                                          firstStarted.countDown();
                                                                          await(releaseFirst);
                                                                      });
            await(firstStarted);
            try {
                MessagingRejectedException failure = assertThrows(
                        MessagingRejectedException.class,
                        () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
                assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
            } finally {
                releaseFirst.countDown();
            }
            await(first);
        }
    }

    @Test
    void boundsAggregatePendingMessageRetention() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(3)
                .maxPendingMessages(2)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);

            AsyncTask firstPending = async(() -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            awaitWaiting(firstPending);
            AsyncTask secondPending = async(() -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            awaitWaiting(secondPending);

            MessagingRejectedException saturated = assertThrows(
                    MessagingRejectedException.class,
                    () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            assertThat(saturated.reason(), is(MessagingRejectedException.Reason.SATURATED));

            releaseActive.countDown();
            await(active);
            await(firstPending);
            await(secondPending);
        }
    }

    @Test
    void immediateAdmissionDoesNotConsumeOrRequirePendingCapacity() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders");
             ConnectorDeliveryReservation ignored = engine.reserveConnectorDelivery("orders", 1)) {
            AtomicBoolean delivered = new AtomicBoolean();
            dispatch(engine, "orders", List.of(message(1)), () -> delivered.set(true));
            assertThat(delivered.get(), is(true));
        }
    }

    @Test
    void directAdmissionRejectsDispatcherContentionWithoutLeakingPendingBudget() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch lockHeld = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);
            AsyncTask lockHolder = async(() -> engine.runWithChannelAdmissionLock("orders", () -> {
                lockHeld.countDown();
                await(releaseLock);
            }));
            await(lockHeld);
            try {
                MessagingRejectedException rejection = assertThrows(
                        MessagingRejectedException.class,
                        () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
                assertThat(rejection.reason(), is(MessagingRejectedException.Reason.SATURATED));
            } finally {
                releaseLock.countDown();
                await(lockHolder);
            }

            AtomicBoolean admitted = new AtomicBoolean();
            dispatch(engine, "orders", List.of(message(1)), () -> admitted.set(true));
            assertThat(admitted.get(), is(true));
        }
    }

    @Test
    void drainWinsOverDispatcherContention() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch lockHeld = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);
            AtomicBoolean delivered = new AtomicBoolean();
            AsyncTask lockHolder = async(() -> engine.runWithChannelAdmissionLock("orders", () -> {
                lockHeld.countDown();
                await(releaseLock);
            }));
            await(lockHeld);
            AsyncTask drain = async(engine::beginDrain);
            try {
                awaitWaiting(drain);

                MessagingRejectedException rejection = assertThrows(
                        MessagingRejectedException.class,
                        () -> dispatch(engine, "orders", List.of(message(1)), () -> delivered.set(true)));

                assertThat(rejection.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
                assertThat(engine.ownsShutdownRejection(rejection), is(true));
                assertThat(delivered.get(), is(false));
            } finally {
                releaseLock.countDown();
                await(lockHolder);
                await(drain);
            }
        }
    }

    @Test
    void drainWaitsForAttemptThatCapturedAncestryBeforeAdmission() throws Exception {
        CountDownLatch ancestryCaptured = new CountDownLatch(1);
        CountDownLatch releaseAdmission = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        CountDownLatch targetStarted = new CountDownLatch(1);
        CountDownLatch releaseTarget = new CountDownLatch(1);
        AtomicBoolean targetCompletedNaturally = new AtomicBoolean();
        AtomicBoolean drained = new AtomicBoolean();
        AtomicReference<Thread> childThread = new AtomicReference<>();
        AtomicReference<Throwable> childFailure = new AtomicReference<>();
        MessagingExecutionConfig config = configBuilder().build();
        Runnable admissionHook = () -> {
            if (Thread.currentThread().getName().equals("delayed-descendant-admission")) {
                ancestryCaptured.countDown();
                await(releaseAdmission);
            }
        };
        try (DeliveryEngine engine = new DeliveryEngine(config, admissionHook)) {
            engine.registerChannel("a", config);
            engine.registerChannel("b", config);
            AsyncTask parent = async(() -> dispatch(engine, "b", List.of(message(1)), () -> {
                Thread child = Thread.ofPlatform().name("delayed-descendant-admission").start(() -> {
                    try {
                        dispatch(engine, "a", List.of(message(2)), () -> {
                            targetStarted.countDown();
                            await(releaseTarget);
                            targetCompletedNaturally.set(true);
                        });
                    } catch (Throwable t) {
                        childFailure.set(t);
                    }
                });
                childThread.set(child);
                await(releaseParent);
            }));
            await(ancestryCaptured);
            engine.beginDrain();
            AsyncTask drain = async(() -> drained.set(engine.awaitDrained(WAIT)));
            try {
                awaitWaiting(drain);
                releaseParent.countDown();
                await(parent);
                awaitWaiting(drain);
                assertThat(targetStarted.getCount(), is(1L));
                assertThat(drained.get(), is(false));

                releaseAdmission.countDown();
                await(targetStarted);
                awaitWaiting(drain);
                assertThat(drained.get(), is(false));
            } finally {
                releaseAdmission.countDown();
                releaseParent.countDown();
                releaseTarget.countDown();
                await(parent);
                Thread child = childThread.get();
                if (child != null) {
                    join(child);
                }
                await(drain);
            }
            assertThat(drained.get(), is(true));
            assertThat(targetCompletedNaturally.get(), is(true));
            assertThat(childFailure.get(), nullValue());
        }
    }

    @Test
    void reservationRejectsDispatcherContentionWithoutLeakingPendingBudget() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch lockHeld = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);
            AsyncTask lockHolder = async(() -> engine.runWithChannelAdmissionLock("orders", () -> {
                lockHeld.countDown();
                await(releaseLock);
            }));
            await(lockHeld);
            try {
                MessagingRejectedException rejection = assertThrows(
                        MessagingRejectedException.class,
                        () -> engine.reserveConnectorDelivery("orders", 1));
                assertThat(rejection.reason(), is(MessagingRejectedException.Reason.SATURATED));
            } finally {
                releaseLock.countDown();
                await(lockHolder);
            }

            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            reservation.close();
            engine.reserveConnectorDelivery("orders", 1).close();
        }
    }

    @Test
    void connectorReservationsBoundPreAcquisitionRetention() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(2)
                .maxPendingMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation full = engine.reserveConnectorDelivery("orders", 2);
            assertThat(engine.tryReserveConnectorDelivery("orders", 1).isEmpty(), is(true));

            full.close();
            ConnectorDeliveryReservation recovered = engine.tryReserveConnectorDelivery("orders", 2)
                    .orElseThrow();
            recovered.close();
        }
    }

    @Test
    void blockingReservationWaitsWithoutRetainingTransportData() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(2)
                .maxPendingMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation first = engine.reserveConnectorDelivery("orders", 1);
            AtomicReference<ConnectorDeliveryReservation> second = new AtomicReference<>();
            AsyncTask waiting = async(() -> second.set(engine.reserveConnectorDelivery("orders", 1)));
            awaitWaiting(waiting);

            first.close();
            await(waiting);
            second.get().close();
        }
    }

    @Test
    void blockingReservationTimeoutDoesNotLeakPendingCapacity() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(2)
                .maxPendingMessages(1)
                .admissionTimeout(Duration.ofMillis(50))
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation first = engine.reserveConnectorDelivery("orders", 1);
            MessagingRejectedException timeout = assertThrows(
                    MessagingRejectedException.class,
                    () -> engine.reserveConnectorDelivery("orders", 1));
            assertThat(timeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));

            first.close();
            ConnectorDeliveryReservation recovered = engine.tryReserveConnectorDelivery("orders", 1)
                    .orElseThrow();
            recovered.close();
        }
    }

    @Test
    void runtimeReservationRejectsActualMessageCountBeyondReservationAndCloses() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            MessagingRejectedException oversized = assertThrows(
                    MessagingRejectedException.class,
                    () -> start(reservation, List.of(message(1), message(1)), () -> { }));
            assertThat(oversized.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
            MessagingRejectedException closed = assertThrows(
                    MessagingRejectedException.class,
                    () -> start(reservation, List.of(message(1)), () -> { }));
            assertThat(closed.reason(), is(MessagingRejectedException.Reason.CANCELLED));

            ConnectorDeliveryReservation recovered = engine.tryReserveConnectorDelivery("orders", 1)
                    .orElseThrow();
            recovered.close();
        }
    }

    @Test
    void reservationStartAtomicallyShrinksAndTransfersToSettlementLease() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(2)
                .maxInFlightMessages(2)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 2);
            ConnectorDelivery delivery = start(reservation, List.of(message(1)), () -> { });
            assertThat(delivery.await(WAIT), is(true));

            ConnectorDeliveryReservation pendingCapacity = engine.tryReserveConnectorDelivery("orders", 2)
                    .orElseThrow();
            pendingCapacity.close();
            assertThat(trySubmitConnectorDelivery(engine, "orders",
                                                  List.of(message(2), message(2)),
                                                  () -> { }).isEmpty(), is(true));

            delivery.close();
            ConnectorDelivery fullDelivery = trySubmitConnectorDelivery(engine, "orders",
                                                                                 List.of(message(2), message(2)),
                                                                                 () -> { })
                    .orElseThrow();
            await(fullDelivery);
        }
    }

    @Test
    void reservationStartLockContentionConsumesAdmissionTimeout() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(50))
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            CountDownLatch lockHeld = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);
            AsyncTask lockHolder = async(() -> engine.runWithChannelAdmissionLock("orders", () -> {
                lockHeld.countDown();
                await(releaseLock);
            }));
            await(lockHeld);

            AsyncTask waiting = async(() -> {
                ConnectorDelivery delivery = start(reservation, List.of(message(1)), () -> { });
                delivery.close();
            });
            try {
                awaitWaiting(waiting);
                Thread.sleep(150);
                assertThat("reservation start did not return when admission lock wait timed out",
                           waiting.completion().isDone(), is(true));
                MessagingRejectedException timeout = assertInstanceOf(
                        MessagingRejectedException.class,
                        failure(waiting));
                assertThat(timeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
            } finally {
                releaseLock.countDown();
                await(lockHolder);
            }

            ConnectorDeliveryReservation recovered = null;
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (recovered == null && System.nanoTime() < deadline) {
                recovered = engine.tryReserveConnectorDelivery("orders", 1).orElse(null);
                Thread.onSpinWait();
            }
            assertThat("deferred reservation cleanup did not restore capacity", recovered != null, is(true));
            recovered.close();
        }
    }

    @Test
    void shutdownSerializesDeferredCleanupThreadRegistration() throws Exception {
        DeliveryEngine engine = engine(configBuilder().build(), "orders");
        CountDownLatch registryHeld = new CountDownLatch(1);
        CountDownLatch releaseRegistry = new CountDownLatch(1);
        AsyncTask registryHolder = async(() -> engine.runWithDispatchThreadRegistryLock(() -> {
            registryHeld.countDown();
            await(releaseRegistry);
        }));
        await(registryHeld);

        AsyncTask shutdown = async(engine::close);
        AsyncTask registration = null;
        try {
            awaitWaiting(shutdown);
            AtomicBoolean cleanupRan = new AtomicBoolean();
            AtomicBoolean cleanupStarted = new AtomicBoolean();
            registration = async(() -> cleanupStarted.set(
                    engine.startCleanup(() -> cleanupRan.set(true))));
            awaitWaiting(registration);

            releaseRegistry.countDown();
            await(registryHolder);
            await(shutdown);
            await(registration);

            assertThat("cleanup thread started after shutdown began", cleanupStarted.get(), is(false));
            assertThat("cleanup ran after shutdown", cleanupRan.get(), is(false));
        } finally {
            releaseRegistry.countDown();
            if (!shutdown.completion().isDone()) {
                await(shutdown);
            }
            if (registration != null && !registration.completion().isDone()) {
                await(registration);
            }
        }
    }

    @Test
    void reservationStartTimeoutReleasesPendingCapacity() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(50))
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);

            MessagingRejectedException timeout = assertThrows(
                    MessagingRejectedException.class,
                    () -> start(reservation, List.of(message(1)), () -> { }));
            assertThat(timeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
            ConnectorDeliveryReservation recovered = engine.tryReserveConnectorDelivery("orders", 1)
                    .orElseThrow();
            recovered.close();

            releaseActive.countDown();
            await(active);
        }
    }

    @Test
    void reservationTryStartRetainsPendingCapacityUntilInFlightIsAvailable() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);

            assertThat(tryStart(reservation, List.of(message(1)), () -> { }).isEmpty(), is(true));
            assertThat(engine.tryReserveConnectorDelivery("orders", 1).isEmpty(), is(true));

            releaseActive.countDown();
            await(active);
            ConnectorDelivery delivery = tryStart(reservation, List.of(message(1)), () -> { })
                    .orElseThrow();
            await(delivery);
        }
    }

    @Test
    void repeatedTryStartCallsShareReservationAdmissionTimeoutBudget() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(100))
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            MessageBatch<Object> batch = batch(List.of(message(2)));

            assertThat(reservation.tryStart(batch).isEmpty(), is(true));
            Thread.sleep(150);
            MessagingRejectedException timeout = assertThrows(MessagingRejectedException.class,
                                                               () -> reservation.tryStart(batch));
            assertThat(timeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));

            releaseActive.countDown();
            await(active);
            engine.reserveConnectorDelivery("orders", 1).close();
        }
    }

    @Test
    void interruptedReservationStartReleasesPendingCapacity() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            AsyncTask waiting = async(() -> start(reservation, List.of(message(1)), () -> { }));
            awaitWaiting(waiting);

            waiting.thread().interrupt();
            MessagingRejectedException cancelled = assertInstanceOf(
                    MessagingRejectedException.class,
                    failure(waiting));
            assertThat(cancelled.reason(), is(MessagingRejectedException.Reason.CANCELLED));
            ConnectorDeliveryReservation recovered = engine.tryReserveConnectorDelivery("orders", 1)
                    .orElseThrow();
            recovered.close();

            releaseActive.countDown();
            await(active);
        }
    }

    @Test
    void reservationAllowsOnlyOneConcurrentStartAttempt() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            AsyncTask firstStart = async(() -> start(reservation, List.of(message(1)), () -> { }));
            awaitWaiting(firstStart);

            assertThrows(IllegalStateException.class,
                         () -> start(reservation, List.of(message(1)), () -> { }));
            reservation.close();
            MessagingRejectedException cancelled = assertInstanceOf(
                    MessagingRejectedException.class,
                    failure(firstStart));
            assertThat(cancelled.reason(), is(MessagingRejectedException.Reason.CANCELLED));

            releaseActive.countDown();
            await(active);
        }
    }

    @Test
    void invalidSecondConcurrentStartCannotCancelFirstStart() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            AtomicReference<ConnectorDelivery> firstDelivery = new AtomicReference<>();
            AsyncTask firstStart = async(() -> firstDelivery.set(
                    start(reservation, List.of(message(1)), () -> { })));
            awaitWaiting(firstStart);

            assertThrows(IllegalStateException.class,
                         () -> start(reservation, List.of(message(2)), () -> { }));

            releaseActive.countDown();
            await(active);
            await(firstStart);
            await(firstDelivery.get());
        }
    }

    @Test
    void reservationAcquisitionTimeDoesNotConsumeCapacityWaitBudget() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(20))
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery("orders", 1);
            Thread.sleep(50);
            ConnectorDelivery delivery = start(reservation, List.of(message(1)), () -> { });
            await(delivery);
        }
    }

    @Test
    void reservationAndStartShareOneCapacityWaitBudget() throws Exception {
        Duration timeout = Duration.ofMillis(500);
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(2)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(timeout)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> {
                        activeStarted.countDown();
                        await(releaseActive);
                    });
            await(activeStarted);
            ConnectorDeliveryReservation first = engine.reserveConnectorDelivery("orders", 1);
            long started = System.nanoTime();
            AsyncTask waiting = async(() -> {
                ConnectorDeliveryReservation second = engine.reserveConnectorDelivery("orders", 1);
                start(second, List.of(message(1)), () -> { });
            });
            awaitWaiting(waiting);
            Thread.sleep(300);
            first.close();

            MessagingRejectedException timedOut = assertInstanceOf(
                    MessagingRejectedException.class,
                    failure(waiting));
            assertThat(timedOut.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat("reserve and start used separate timeout budgets: " + elapsedMillis + "ms",
                       elapsedMillis < 700, is(true));

            releaseActive.countDown();
            await(active);
        }
    }

    @Test
    void closingOrShuttingDownReservationReleasesCapacityExactlyOnce() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .build();
        DeliveryEngine engine = engine(config, "orders");
        ConnectorDeliveryReservation closedReservation = engine.reserveConnectorDelivery("orders", 1);
        closedReservation.close();
        closedReservation.close();
        MessagingRejectedException cancelled = assertThrows(
                MessagingRejectedException.class,
                () -> start(closedReservation, List.of(message(1)), () -> { }));
        assertThat(cancelled.reason(), is(MessagingRejectedException.Reason.CANCELLED));

        ConnectorDeliveryReservation shutdownReservation = engine.reserveConnectorDelivery("orders", 1);
        engine.close();
        MessagingRejectedException shutdown = assertThrows(
                MessagingRejectedException.class,
                () -> start(shutdownReservation, List.of(message(1)), () -> { }));
        assertThat(shutdown.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
    }

    @Test
    void runtimeDeliveryLimitFitsBothMessageBudgets() {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingMessages(3)
                .maxInFlightMessages(5)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            assertThat(engine.maxDeliveryMessages("orders"), is(3));
        }
    }

    @Test
    void validatesExecutionLimitsAndDefaults() {
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().queueCapacity(-1).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().maxPendingAdmissions(0).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().maxPendingMessages(0).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().maxInFlightMessages(0).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().admissionTimeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().admissionTimeout(Duration.ofNanos(-1)).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder()
                             .admissionTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().shutdownTimeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder().shutdownTimeout(Duration.ofNanos(-1)).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessagingExecutionConfig.builder()
                             .shutdownTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                             .build());

        MessagingExecutionConfig minimums = MessagingExecutionConfig.builder()
                .queueCapacity(0)
                .maxPendingAdmissions(1)
                .maxPendingMessages(1)
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofNanos(1))
                .shutdownTimeout(Duration.ofNanos(1))
                .build();
        assertThat(minimums.queueCapacity(), is(0));
        assertThat(minimums.maxPendingAdmissions(), is(1));
        assertThat(minimums.maxPendingMessages(), is(1));
        assertThat(minimums.maxInFlightMessages(), is(1));
        assertThat(minimums.admissionTimeout().orElseThrow(), is(Duration.ofNanos(1)));
        assertThat(minimums.shutdownTimeout(), is(Duration.ofNanos(1)));

        MessagingExecutionConfig defaults = MessagingExecutionConfig.builder().build();
        assertThat(defaults.queueCapacity(), is(0));
        assertThat(defaults.maxPendingAdmissions(), is(64));
        assertThat(defaults.maxPendingMessages(), is(1024));
        assertThat(defaults.maxInFlightMessages(), is(1024));
        assertThat(defaults.admissionTimeout().isEmpty(), is(true));
        assertThat(defaults.shutdownTimeout(), is(Duration.ofSeconds(10)));
    }

    @Test
    void dispatchesQueuedTasksInFifoOrder() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(2)
                .maxInFlightMessages(3)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            List<Integer> order = new CopyOnWriteArrayList<>();

            ConnectorDelivery first = submitConnectorDelivery(engine, "orders",
                                                                      List.of(message(1)),
                                                                      () -> {
                                                                          order.add(1);
                                                                          firstStarted.countDown();
                                                                          await(releaseFirst);
                                                                      });
            await(firstStarted);
            ConnectorDelivery second = submitConnectorDelivery(engine, "orders",
                                                                       List.of(message(1)),
                                                                       () -> order.add(2));
            ConnectorDelivery third = submitConnectorDelivery(engine, "orders",
                                                                      List.of(message(1)),
                                                                      () -> order.add(3));

            assertThat(order, is(List.of(1)));
            releaseFirst.countDown();
            await(first);
            await(second);
            await(third);
            assertThat(order, is(List.of(1, 2, 3)));
        }
    }

    @Test
    void interruptionCancelsAdmissionAndActiveConnectorDelivery() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(0)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean rejectedActionRan = new AtomicBoolean();
            ConnectorDelivery first = submitConnectorDelivery(engine, "orders",
                                                                      List.of(message(1)),
                                                                      () -> {
                                                                          firstStarted.countDown();
                                                                          await(releaseFirst);
                                                                      });
            await(firstStarted);

            AsyncTask waiting = async(() -> dispatch(engine, "orders",
                                                            List.of(message(1)),
                                                            () -> rejectedActionRan.set(true)));
            awaitWaiting(waiting);
            waiting.thread().interrupt();

            Throwable waitingFailure = failure(waiting);
            MessagingRejectedException rejection =
                    assertInstanceOf(MessagingRejectedException.class, waitingFailure);
            assertThat(rejection.reason(), is(MessagingRejectedException.Reason.CANCELLED));
            assertThat(waiting.thread().isInterrupted(), is(true));
            assertThat(rejectedActionRan.get(), is(false));

            releaseFirst.countDown();
            await(first);
            dispatch(engine, "orders", List.of(message(1)), () -> { });
        }

        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine, "orders",
                                                                       List.of(message(1)),
                                                                       () -> {
                                                                           activeStarted.countDown();
                                                                           awaitInterruption(interrupted);
                                                                       });
            await(activeStarted);

            active.cancel();
            MessagingRejectedException cancellation = assertThrows(
                    MessagingRejectedException.class,
                    () -> active.await(WAIT));
            assertThat(cancellation.reason(), is(MessagingRejectedException.Reason.CANCELLED));
            await(interrupted);

            assertThat(trySubmitConnectorDelivery(engine, "orders",
                                                  List.of(message(1)),
                                                  () -> { }).isEmpty(), is(true));
            active.close();
            AtomicBoolean nextRan = new AtomicBoolean();
            dispatch(engine, "orders", List.of(message(1)), () -> nextRan.set(true));
            assertThat(nextRan.get(), is(true));
        }
    }

    @Test
    void releasesPermitsWhenDeliveryFails() {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(0)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            IllegalStateException expected = new IllegalStateException("failed");

            IllegalStateException actual = assertThrows(
                    IllegalStateException.class,
                    () -> dispatch(engine, "orders", List.of(message(1)), () -> {
                        throw expected;
                    }));
            assertThat(actual, sameInstance(expected));

            AtomicBoolean nextRan = new AtomicBoolean();
            dispatch(engine, "orders", List.of(message(1)), () -> nextRan.set(true));
            assertThat(nextRan.get(), is(true));
        }
    }

    @Test
    void connectorDeliveryRetainsLeaseAcrossRetryStyleDispatch() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(0)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch firstAttemptStarted = new CountDownLatch(1);
            CountDownLatch allowRetry = new CountDownLatch(1);
            CountDownLatch competitorStarted = new CountDownLatch(1);
            List<String> order = new CopyOnWriteArrayList<>();
            Message<?> retained = message(1);
            MessageBatch<?> retainedBatch = batch(List.of(retained));
            Runnable retryWork = () -> {
                dispatch(engine, "orders", retainedBatch, () -> {
                    order.add("attempt-1");
                    firstAttemptStarted.countDown();
                    await(allowRetry);
                });
                dispatch(engine, "orders", retainedBatch, () -> order.add("attempt-2"));
            };

            ConnectorDelivery delivery = engine.submitConnectorDelivery("orders", retainedBatch, retryWork);
            await(firstAttemptStarted);

            AsyncTask competitor = async(() -> dispatch(engine, "orders",
                                                               List.of(message(1)),
                                                               () -> {
                                                                   order.add("competitor");
                                                                   competitorStarted.countDown();
                                                               }));
            awaitWaiting(competitor);
            assertThat(competitorStarted.getCount(), is(1L));

            allowRetry.countDown();
            await(delivery);
            await(competitor);
            assertThat(order, is(List.of("attempt-1", "attempt-2", "competitor")));
        }
    }

    @Test
    void connectorLeaseAcceptsLineageSubsetAndRejectsReplacementEnvelopes() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxInFlightMessages(3)
                .build();
        MessageBatch<String> retainedBatch = MessageBatch.<String>builder()
                .id("retained-batch")
                .messages(List.of(message(1), message(1), message(1)))
                .build();
        MessageBatch<String> retryBatch = retainedBatch.subset(List.of(0, 2));
        MessageBatch<String> replacementBatch = retryBatch.derive(
                List.of(message(1), message(1)));
        MessageBatch<String> rebuiltBatch = MessageBatch.<String>builder()
                .id(retainedBatch.id())
                .messages(retainedBatch.messages())
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            AtomicBoolean subsetEmitted = new AtomicBoolean();
            ConnectorDelivery delivery = engine.submitConnectorDelivery(
                    "orders",
                    retainedBatch,
                    () -> {
                        dispatch(engine, "orders", retryBatch, () -> subsetEmitted.set(true));
                        MessagingRejectedException failure = assertThrows(
                                MessagingRejectedException.class,
                                () -> dispatch(engine, "orders", replacementBatch, () -> { }));
                        assertThat(failure.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
                        MessagingRejectedException rebuiltFailure = assertThrows(
                                MessagingRejectedException.class,
                                () -> dispatch(engine, "orders", rebuiltBatch, () -> { }));
                        assertThat(rebuiltFailure.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
                    });

            await(delivery);
            assertThat(subsetEmitted.get(), is(true));
        }
    }

    @Test
    void connectorDeliveryRetainsAdmissionThroughSettlementLease() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDelivery delivery = submitConnectorDelivery(engine, "orders",
                                                                         List.of(message(1)),
                                                                         () -> { });
            assertThat(delivery.await(WAIT), is(true));

            CountDownLatch competitorStarted = new CountDownLatch(1);
            AsyncTask competitor = async(() -> dispatch(engine, "orders",
                                                               List.of(message(1)),
                                                               competitorStarted::countDown));
            awaitWaiting(competitor);
            assertThat(competitorStarted.getCount(), is(1L));

            delivery.close();
            await(competitor);
            assertThat(competitorStarted.getCount(), is(0L));
        }
    }

    @Test
    void boundsPendingAdmissionCallersAndSupportsNonBlockingConnectorAdmission() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxPendingAdmissions(1)
                .maxInFlightMessages(1)
                .build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            CountDownLatch activeStarted = new CountDownLatch(1);
            CountDownLatch releaseActive = new CountDownLatch(1);
            ConnectorDelivery active = submitConnectorDelivery(engine, "orders",
                                                                       List.of(message(1)),
                                                                       () -> {
                                                                           activeStarted.countDown();
                                                                           await(releaseActive);
                                                                       });
            await(activeStarted);

            AsyncTask pending = async(() -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            awaitWaiting(pending);

            MessagingRejectedException saturated = assertThrows(
                    MessagingRejectedException.class,
                    () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            assertThat(saturated.reason(), is(MessagingRejectedException.Reason.SATURATED));
            assertThat(trySubmitConnectorDelivery(engine, "orders",
                                                  List.of(message(1)),
                                                  () -> { }).isEmpty(), is(true));

            releaseActive.countDown();
            await(active);
            await(pending);
        }
    }

    @Test
    void concurrentCrossChannelCycleRejectsInsteadOfWaitingForCapacity() throws Exception {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "a", "b")) {
            CountDownLatch rootsStarted = new CountDownLatch(2);
            CountDownLatch nestedRejected = new CountDownLatch(2);
            Runnable aToB = () -> {
                rootsStarted.countDown();
                await(rootsStarted);
                try {
                    dispatch(engine, "b", List.of(message(1)), () -> { });
                    fail("nested delivery unexpectedly ran");
                } catch (MessagingRejectedException e) {
                    nestedRejected.countDown();
                    await(nestedRejected);
                    throw e;
                }
            };
            Runnable bToA = () -> {
                rootsStarted.countDown();
                await(rootsStarted);
                try {
                    dispatch(engine, "a", List.of(message(1)), () -> { });
                    fail("nested delivery unexpectedly ran");
                } catch (MessagingRejectedException e) {
                    nestedRejected.countDown();
                    await(nestedRejected);
                    throw e;
                }
            };

            AsyncTask first = async(() -> dispatch(engine, "a", List.of(message(1)), aToB));
            AsyncTask second = async(() -> dispatch(engine, "b", List.of(message(1)), bToA));

            MessagingRejectedException firstFailure =
                    assertInstanceOf(MessagingRejectedException.class, failure(first));
            MessagingRejectedException secondFailure =
                    assertInstanceOf(MessagingRejectedException.class, failure(second));
            assertThat(firstFailure.reason(), is(MessagingRejectedException.Reason.SATURATED));
            assertThat(secondFailure.reason(), is(MessagingRejectedException.Reason.SATURATED));
        }
    }

    @Test
    void nestedCycleAcrossIndependentChannelEnginesAlsoRejects() {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine firstEngine = engine(config, "a");
             DeliveryEngine secondEngine = engine(config, "b")) {
            CountDownLatch rootsStarted = new CountDownLatch(2);
            CountDownLatch nestedRejected = new CountDownLatch(2);
            Runnable aToB = () -> rejectTogether(
                    rootsStarted,
                    nestedRejected,
                    () -> dispatch(secondEngine, "b", List.of(message(1)), () -> { }));
            Runnable bToA = () -> rejectTogether(
                    rootsStarted,
                    nestedRejected,
                    () -> dispatch(firstEngine, "a", List.of(message(1)), () -> { }));

            AsyncTask first = async(() -> dispatch(firstEngine, "a", List.of(message(1)), aToB));
            AsyncTask second = async(() -> dispatch(secondEngine, "b", List.of(message(1)), bToA));

            assertThat(assertInstanceOf(MessagingRejectedException.class, failure(first)).reason(),
                       is(MessagingRejectedException.Reason.SATURATED));
            assertThat(assertInstanceOf(MessagingRejectedException.class, failure(second)).reason(),
                       is(MessagingRejectedException.Reason.SATURATED));
        }
    }

    @Test
    void connectorLeaseRejectsDifferentMessageWithinItsReservation() throws Exception {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "orders")) {
            ConnectorDelivery delivery = submitConnectorDelivery(engine,
                    "orders",
                    List.of(message(1)),
                    () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
            try {
                MessagingRejectedException failure = assertThrows(
                        MessagingRejectedException.class,
                        () -> delivery.await(WAIT));
                assertThat(failure.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
            } finally {
                delivery.close();
            }
        }
    }

    @Test
    void connectorLeaseAcceptsItsRetainedEnvelope() throws Exception {
        MessagingExecutionConfig config = configBuilder().build();
        List<Message<String>> retained = List.of(message(1));
        MessageBatch<?> retainedBatch = batch(retained);
        try (DeliveryEngine engine = engine(config, "orders")) {
            AtomicBoolean emitted = new AtomicBoolean();
            ConnectorDelivery delivery = engine.submitConnectorDelivery(
                    "orders",
                    retainedBatch,
                    () -> dispatch(engine, "orders", retainedBatch, () -> emitted.set(true)));
            await(delivery);
            assertThat(emitted.get(), is(true));
        }
    }

    @Test
    void shutdownRejectsQueuedAndNewWorkAndInterruptsActiveAndSourceTasks() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .queueCapacity(1)
                .maxInFlightMessages(2)
                .shutdownTimeout(WAIT)
                .build();
        DeliveryEngine engine = engine(config, "orders");
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch activeInterrupted = new CountDownLatch(1);
        CountDownLatch sourceStarted = new CountDownLatch(1);
        CountDownLatch sourceInterrupted = new CountDownLatch(1);
        AtomicBoolean queuedRan = new AtomicBoolean();

        ConnectorDelivery active = submitConnectorDelivery(engine, "orders",
                                                                   List.of(message(1)),
                                                                   () -> {
                                                                       activeStarted.countDown();
                                                                       awaitInterruption(activeInterrupted);
                                                                   });
        await(activeStarted);
        ConnectorDelivery queued = submitConnectorDelivery(engine, "orders",
                                                                   List.of(message(1)),
                                                                   () -> queuedRan.set(true));
        engine.startSource("orders-source", () -> {
            sourceStarted.countDown();
            awaitInterruption(sourceInterrupted);
        });
        await(sourceStarted);

        engine.close();

        await(activeInterrupted);
        await(sourceInterrupted);
        MessagingRejectedException activeFailure = assertThrows(
                MessagingRejectedException.class,
                () -> active.await(WAIT));
        active.close();
        assertThat(activeFailure.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
        MessagingRejectedException queuedFailure = assertThrows(
                MessagingRejectedException.class,
                () -> queued.await(WAIT));
        assertThat(queuedFailure.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
        assertThat(queuedRan.get(), is(false));

        MessagingRejectedException dispatchFailure = assertThrows(
                MessagingRejectedException.class,
                () -> dispatch(engine, "orders", List.of(message(1)), () -> { }));
        MessagingRejectedException sourceFailure = assertThrows(
                MessagingRejectedException.class,
                () -> engine.startSource("new-source", () -> { }));
        assertThat(dispatchFailure.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
        assertThat(sourceFailure.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
    }

    @Test
    void sourceCompletionListenerRunsAfterDeregistrationWithPublishedFailure() throws Exception {
        IllegalStateException sourceFailure = new IllegalStateException("source failed");
        CountDownLatch sourceStarted = new CountDownLatch(1);
        CountDownLatch releaseSource = new CountDownLatch(1);
        CountDownLatch completionObserved = new CountDownLatch(1);
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        AtomicBoolean trackedDuringCompletion = new AtomicBoolean();
        try (DeliveryEngine engine = engine(configBuilder().build())) {
            DeliveryEngine.SourceTask sourceTask = engine.startSource("source", () -> {
                sourceStarted.countDown();
                await(releaseSource);
                throw sourceFailure;
            });
            await(sourceStarted);
            sourceTask.onCompletion(failure -> {
                observedFailure.set(failure.orElse(null));
                trackedDuringCompletion.set(engine.isCurrentDeliveryOrSourceThread());
                completionObserved.countDown();
            });

            releaseSource.countDown();
            await(completionObserved);

            assertThat(observedFailure.get(), sameInstance(sourceFailure));
            assertThat(sourceTask.failure().orElseThrow(), sameInstance(sourceFailure));
            assertThat(trackedDuringCompletion.get(), is(false));
            assertThat(sourceTask.await(WAIT), is(true));
            assertThat(engine.awaitTermination(WAIT), is(true));
        }
    }

    @Test
    void nestedChannelDispatchWorksAndSameChannelRecursionIsRejected() {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "a", "b")) {
            AtomicBoolean nestedRan = new AtomicBoolean();
            dispatch(engine, "a",
                            List.of(message(1)),
                            () -> dispatch(engine, "b",
                                                  List.of(message(1)),
                                                  () -> nestedRan.set(true)));
            assertThat(nestedRan.get(), is(true));

            MessagingException failure = assertThrows(
                    MessagingException.class,
                    () -> dispatch(engine, "a",
                                          List.of(message(1)),
                                          () -> dispatch(engine, "a",
                                                                List.of(message(1)),
                                                                () -> { })));
            assertThat(failure.getMessage(), containsString("a -> a"));
        }
    }

    @Test
    void joinedChildSameChannelEmissionIsRejected() {
        for (ThreadFactory threadFactory : List.of(Thread.ofVirtual().factory(), Thread.ofPlatform().factory())) {
            MessagingExecutionConfig config = configBuilder()
                    .maxInFlightMessages(1)
                    .admissionTimeout(Duration.ofMillis(100))
                    .build();
            try (DeliveryEngine engine = engine(config, "orders")) {
                AtomicReference<Throwable> childFailure = new AtomicReference<>();
                AtomicBoolean childActionRan = new AtomicBoolean();

                dispatch(engine, "orders", List.of(message(1)), () -> {
                    Thread child = threadFactory.newThread(() -> {
                        try {
                            dispatch(engine, "orders", List.of(message(2)), () -> childActionRan.set(true));
                        } catch (Throwable t) {
                            childFailure.set(t);
                        }
                    });
                    child.start();
                    join(child);
                });

                assertThat(childFailure.get(), instanceOf(MessagingException.class));
                assertThat(childFailure.get().getMessage(), containsString("orders -> orders"));
                assertThat(childActionRan.get(), is(false));
            }
        }
    }

    @Test
    void joinedChildCrossChannelCycleIsRejected() {
        for (ThreadFactory threadFactory : List.of(Thread.ofVirtual().factory(), Thread.ofPlatform().factory())) {
            MessagingExecutionConfig config = configBuilder()
                    .maxInFlightMessages(1)
                    .admissionTimeout(Duration.ofMillis(100))
                    .build();
            try (DeliveryEngine engine = engine(config, "a", "b")) {
                AtomicReference<Throwable> childFailure = new AtomicReference<>();
                AtomicBoolean recursiveActionRan = new AtomicBoolean();

                dispatch(engine, "a", List.of(message(1)), () -> {
                    Thread child = threadFactory.newThread(() -> {
                        try {
                            dispatch(engine,
                                     "b",
                                     List.of(message(2)),
                                     () -> dispatch(engine,
                                                    "a",
                                                    List.of(message(3)),
                                                    () -> recursiveActionRan.set(true)));
                        } catch (Throwable t) {
                            childFailure.set(t);
                        }
                    });
                    child.start();
                    join(child);
                });

                assertThat(childFailure.get(), instanceOf(MessagingException.class));
                assertThat(childFailure.get().getMessage(), containsString("a -> b -> a"));
                assertThat(recursiveActionRan.get(), is(false));
            }
        }
    }

    @Test
    void joinedChildDifferentChannelEmissionCompletes() {
        for (ThreadFactory threadFactory : List.of(Thread.ofVirtual().factory(), Thread.ofPlatform().factory())) {
            MessagingExecutionConfig config = configBuilder()
                    .maxInFlightMessages(1)
                    .admissionTimeout(Duration.ofMillis(100))
                    .build();
            try (DeliveryEngine engine = engine(config, "a", "b")) {
                AtomicReference<Throwable> childFailure = new AtomicReference<>();
                AtomicBoolean childActionRan = new AtomicBoolean();

                dispatch(engine, "a", List.of(message(1)), () -> {
                    Thread child = threadFactory.newThread(() -> {
                        try {
                            dispatch(engine, "b", List.of(message(2)), () -> childActionRan.set(true));
                        } catch (Throwable t) {
                            childFailure.set(t);
                        }
                    });
                    child.start();
                    join(child);
                });

                assertThat(childFailure.get(), nullValue());
                assertThat(childActionRan.get(), is(true));
            }
        }
    }

    @Test
    void childCreatedDuringDeliveryUsesTopLevelAdmissionAfterParentCompletes() {
        for (ThreadFactory threadFactory : List.of(Thread.ofVirtual().factory(), Thread.ofPlatform().factory())) {
            MessagingExecutionConfig config = configBuilder()
                    .maxInFlightMessages(1)
                    .admissionTimeout(Duration.ofMillis(100))
                    .build();
            try (DeliveryEngine engine = engine(config, "orders")) {
                AtomicReference<Throwable> childFailure = new AtomicReference<>();
                AtomicReference<Thread> childThread = new AtomicReference<>();
                AtomicBoolean childActionRan = new AtomicBoolean();

                dispatch(engine, "orders", List.of(message(1)), () -> childThread.set(threadFactory.newThread(() -> {
                    try {
                        dispatch(engine, "orders", List.of(message(2)), () -> childActionRan.set(true));
                    } catch (Throwable t) {
                        childFailure.set(t);
                    }
                })));

                childThread.get().start();
                join(childThread.get());

                assertThat(childFailure.get(), nullValue());
                assertThat(childActionRan.get(), is(true));
            }
        }
    }

    @Test
    void joinedChildCannotSubmitConnectorDelivery() {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "a", "b")) {
            AtomicReference<Throwable> childFailure = new AtomicReference<>();
            AtomicBoolean connectorActionRan = new AtomicBoolean();

            dispatch(engine, "a", List.of(message(1)), () -> {
                Thread child = Thread.ofVirtual().start(() -> {
                    try (ConnectorDelivery delivery = submitConnectorDelivery(engine,
                                                                              "b",
                                                                              List.of(message(2)),
                                                                              () -> connectorActionRan.set(true))) {
                        delivery.await();
                    } catch (Throwable t) {
                        childFailure.set(t);
                    }
                });
                join(child);
            });

            assertThat(childFailure.get(), instanceOf(MessagingException.class));
            assertThat(childFailure.get().getMessage(), containsString("cannot be submitted from messaging dispatch"));
            assertThat(connectorActionRan.get(), is(false));
        }
    }

    @Test
    void joinedChildCannotBorrowConnectorDeliveryLease() throws Exception {
        MessagingExecutionConfig config = configBuilder()
                .maxInFlightMessages(1)
                .admissionTimeout(Duration.ofMillis(100))
                .build();
        MessageBatch<?> retainedBatch = batch(List.of(message(1)));
        try (DeliveryEngine engine = engine(config, "orders");
             ConnectorDelivery delivery = engine.submitConnectorDelivery("orders", retainedBatch, () -> {
                 AtomicReference<Throwable> childFailure = new AtomicReference<>();
                 AtomicBoolean childActionRan = new AtomicBoolean();
                 Thread child = Thread.ofVirtual().start(() -> {
                     try {
                         dispatch(engine, "orders", retainedBatch, () -> childActionRan.set(true));
                     } catch (Throwable t) {
                         childFailure.set(t);
                     }
                 });
                 join(child);
                 assertThat(childFailure.get(), instanceOf(MessagingException.class));
                 assertThat(childFailure.get().getMessage(), containsString("orders -> orders"));
                 assertThat(childActionRan.get(), is(false));
             })) {
            assertThat(delivery.await(WAIT), is(true));
        }
    }

    @Test
    void connectorDeliveryCannotBeSubmittedFromMessagingDispatch() {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine engine = engine(config, "a", "b")) {
            MessagingException failure = assertThrows(
                    MessagingException.class,
                    () -> dispatch(engine,
                            "a",
                            List.of(message(1)),
                            () -> submitConnectorDelivery(engine, "b",
                                                                 List.of(message(1)),
                                                                 () -> { })));
            assertThat(failure.getMessage(), containsString("cannot be submitted from messaging dispatch"));
        }
    }

    @Test
    void sameNamedChannelsInDifferentEnginesAreDistinctCycleNodes() {
        MessagingExecutionConfig config = configBuilder().build();
        try (DeliveryEngine firstEngine = engine(config, "orders");
             DeliveryEngine secondEngine = engine(config, "orders")) {
            AtomicBoolean nestedRan = new AtomicBoolean();
            dispatch(firstEngine, "orders",
                                 List.of(message(1)),
                                 () -> dispatch(secondEngine, "orders",
                                                             List.of(message(1)),
                                                             () -> nestedRan.set(true)));
            assertThat(nestedRan.get(), is(true));

            MessagingException cycle = assertThrows(
                    MessagingException.class,
                    () -> dispatch(firstEngine,
                            "orders",
                            List.of(message(1)),
                            () -> dispatch(secondEngine,
                                    "orders",
                                    List.of(message(1)),
                                    () -> dispatch(firstEngine, "orders",
                                                               List.of(message(1)),
                                                               () -> { }))));
            assertThat(cycle.getMessage(), containsString("orders -> orders -> orders"));
        }
    }

    private static MessagingExecutionConfig.Builder configBuilder() {
        return MessagingExecutionConfig.builder()
                .queueCapacity(0)
                .maxInFlightMessages(10)
                .shutdownTimeout(WAIT);
    }

    private static DeliveryEngine engine(MessagingExecutionConfig config, String... channels) {
        DeliveryEngine engine = new DeliveryEngine(config);
        for (String channel : channels) {
            engine.registerChannel(channel, config);
        }
        return engine;
    }

    private static void dispatch(DeliveryEngine engine,
                                 String channel,
                                 List<? extends Message<?>> messages,
                                 Runnable action) {
        engine.dispatch(channel, MessageBatch.create(messages), action);
    }

    private static void dispatch(DeliveryEngine engine,
                                 String channel,
                                 MessageBatch<?> messages,
                                 Runnable action) {
        engine.dispatch(channel, messages, action);
    }

    private static ConnectorDelivery submitConnectorDelivery(DeliveryEngine engine,
                                                              String channel,
                                                              List<? extends Message<?>> messages,
                                                              Runnable action) {
        return engine.submitConnectorDelivery(channel, batch(messages), action);
    }

    private static java.util.Optional<ConnectorDelivery> trySubmitConnectorDelivery(
            DeliveryEngine engine,
            String channel,
            List<? extends Message<?>> messages,
            Runnable action) {
        return engine.trySubmitConnectorDelivery(channel, batch(messages), action);
    }

    private static ConnectorDelivery start(ConnectorDeliveryReservation reservation,
                                           List<? extends Message<?>> messages,
                                           Runnable action) {
        ConnectorDelivery delivery = reservation.start(batch(messages));
        action.run();
        return delivery;
    }

    private static java.util.Optional<ConnectorDelivery> tryStart(ConnectorDeliveryReservation reservation,
                                                                 List<? extends Message<?>> messages,
                                                                 Runnable action) {
        java.util.Optional<ConnectorDelivery> delivery = reservation.tryStart(batch(messages));
        delivery.ifPresent(ignored -> action.run());
        return delivery;
    }

    private static MessageBatch<Object> batch(List<? extends Message<?>> messages) {
        return MessageBatch.builder()
                .messages(messages)
                .build();
    }

    private static Message<String> message(long id) {
        return Message.create(Long.toString(id));
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

    private static void rejectTogether(CountDownLatch rootsStarted,
                                       CountDownLatch nestedRejected,
                                       Runnable nestedDelivery) {
        rootsStarted.countDown();
        await(rootsStarted);
        try {
            nestedDelivery.run();
            fail("nested delivery unexpectedly ran");
        } catch (MessagingRejectedException e) {
            nestedRejected.countDown();
            await(nestedRejected);
            throw e;
        }
    }

    private static void await(AsyncTask task) throws Exception {
        task.completion().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void await(ConnectorDelivery delivery) throws InterruptedException {
        try {
            assertThat("delivery did not complete", delivery.await(WAIT), is(true));
        } finally {
            delivery.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("latch did not open", latch.await(WAIT.toMillis(), TimeUnit.MILLISECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting latch", e);
        }
    }

    private static void awaitInterruption(CountDownLatch interrupted) {
        try {
            new CountDownLatch(1).await();
            fail("blocking task unexpectedly resumed");
        } catch (InterruptedException e) {
            interrupted.countDown();
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitWaiting(AsyncTask task) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        Thread.State state;
        do {
            if (task.completion().isDone()) {
                fail("task completed instead of waiting");
            }
            state = task.thread().getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        fail("task did not enter a waiting state; last state was " + state);
    }

    private static void join(Thread thread) {
        boolean interrupted = false;
        try {
            thread.join(WAIT.toMillis());
        } catch (InterruptedException e) {
            interrupted = true;
        }
        boolean timedOut = thread.isAlive();
        if (interrupted || timedOut) {
            thread.interrupt();
            try {
                thread.join(WAIT.toMillis());
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while joining child thread");
        }
        if (thread.isAlive()) {
            throw new AssertionError("Child thread did not terminate");
        }
        if (timedOut) {
            throw new AssertionError("Child thread did not terminate without interruption");
        }
    }

    private static Throwable failure(AsyncTask task) {
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> task.completion().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        return exception.getCause();
    }

    private static <T> T assertInstanceOf(Class<T> type, Object value) {
        assertThat(value, is(instanceOf(type)));
        return type.cast(value);
    }

    private record AsyncTask(Thread thread, CompletableFuture<Void> completion) {
    }
}
