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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Runtime-owned delivery and source task engine.
 */
final class DeliveryEngine implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DeliveryEngine.class.getName());
    private static final ThreadLocal<DeliveryContext> CURRENT_DELIVERY = new ThreadLocal<>();

    private final Map<String, ChannelDispatcher> dispatchers = new ConcurrentHashMap<>();
    private final Set<Thread> sourceThreads = ConcurrentHashMap.newKeySet();
    private final Set<Thread> dispatchThreads = ConcurrentHashMap.newKeySet();
    private final ReentrantLock sourceThreadsLock = new ReentrantLock();
    private final ReentrantLock dispatchThreadsLock = new ReentrantLock();
    private final ThreadFactory dispatchThreadFactory;
    private final ThreadFactory cleanupThreadFactory;
    private final ThreadFactory sourceThreadFactory;
    private final Duration shutdownTimeout;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();

    DeliveryEngine(MessagingExecutionConfig defaultConfig) {
        this.shutdownTimeout = Objects.requireNonNull(defaultConfig).shutdownTimeout();
        this.dispatchThreadFactory = virtualThreadFactory("helidon-messaging-dispatch-", "Messaging delivery failed");
        this.cleanupThreadFactory = virtualThreadFactory("helidon-messaging-cleanup-",
                                                         "Messaging reservation cleanup failed");
        this.sourceThreadFactory = virtualThreadFactory("helidon-messaging-source-", "Messaging source failed");
    }

    void registerChannel(String channel, MessagingExecutionConfig config) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(config);
        if (!accepting.get() || closed.get()) {
            throw rejected(channel,
                           MessagingRejectedException.Reason.SHUTDOWN,
                           "Messaging runtime is shutting down");
        }
        ChannelDispatcher previous = dispatchers.putIfAbsent(channel, new ChannelDispatcher(channel, config));
        if (previous != null) {
            throw new IllegalArgumentException("Messaging channel already registered: " + channel);
        }
    }

    void dispatch(String channel,
                  MessageBatch<?> batch,
                  Runnable action) {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(action);
        ChannelDispatcher dispatcher = dispatcher(channel);
        DeliveryContext parent = CURRENT_DELIVERY.get();
        if (parent != null && parent.connectorLease(this, channel)) {
            parent.dispatchWithinLease(batch, action);
            return;
        }
        if (parent != null && parent.path().contains(new DeliveryNode(this, channel))) {
            throw new MessagingException("Cyclic synchronous messaging emission: "
                                                 + String.join(" -> ", parent.pathNames()) + " -> " + channel);
        }
        DeliveryTask task;
        try {
            AdmissionMode admissionMode = parent == null ? AdmissionMode.WAIT : AdmissionMode.NESTED;
            task = dispatcher.submit(batch.size(),
                                     parent == null ? List.of() : parent.path(),
                                     false,
                                     admissionMode,
                                     null,
                                     action);
            if (task == null) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.SATURATED,
                               "Nested delivery cannot run immediately on channel " + channel);
            }
        } catch (MessagingRejectedException e) {
            if (parent != null && canMarkNotAttempted(e)) {
                throw new PreDispatchRejectedException(e);
            }
            throw e;
        }
        awaitCaller(task);
    }

    ConnectorDelivery submitConnectorDelivery(String channel,
                                               MessageBatch<?> batch,
                                               Runnable action) {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(action);
        ChannelDispatcher dispatcher = dispatcher(channel);
        DeliveryContext parent = CURRENT_DELIVERY.get();
        if (parent != null) {
            throw new MessagingException("A connector delivery cannot be submitted from messaging dispatch");
        }
        return dispatcher.submit(batch.size(),
                                 List.of(),
                                 true,
                                 AdmissionMode.WAIT,
                                 batch,
                                 action);
    }

    Optional<ConnectorDelivery> trySubmitConnectorDelivery(String channel,
                                                           MessageBatch<?> batch,
                                                           Runnable action) {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(action);
        DeliveryContext parent = CURRENT_DELIVERY.get();
        if (parent != null) {
            throw new MessagingException("A connector delivery cannot be submitted from messaging dispatch");
        }
        DeliveryTask task = dispatcher(channel).submit(batch.size(),
                                                       List.of(),
                                                       true,
                                                       AdmissionMode.TRY,
                                                       batch,
                                                       action);
        return Optional.ofNullable(task);
    }

    ConnectorDeliveryReservation reserveConnectorDelivery(String channel,
                                                           int maxMessages,
                                                           Consumer<MessageBatch<?>> processor) {
        rejectConnectorReservationFromDispatch();
        return dispatcher(channel).reserveConnectorDelivery(
                connectorReservationMessages(channel, maxMessages),
                AdmissionMode.WAIT,
                -1,
                Objects.requireNonNull(processor));
    }

    Optional<ConnectorDeliveryReservation> tryReserveConnectorDelivery(String channel,
                                                                       int maxMessages,
                                                                       long remainingCapacityWaitNanos,
                                                                       Consumer<MessageBatch<?>> processor) {
        rejectConnectorReservationFromDispatch();
        if (remainingCapacityWaitNanos < 0) {
            throw new IllegalArgumentException("remainingCapacityWaitNanos must be zero or greater");
        }
        return Optional.ofNullable(dispatcher(channel).reserveConnectorDelivery(
                connectorReservationMessages(channel, maxMessages),
                AdmissionMode.TRY,
                remainingCapacityWaitNanos,
                Objects.requireNonNull(processor)));
    }

    ConnectorDeliveryReservation reserveConnectorDelivery(String channel,
                                                           int maxMessages) {
        return reserveConnectorDelivery(channel, maxMessages, ignored -> { });
    }

    Optional<ConnectorDeliveryReservation> tryReserveConnectorDelivery(String channel,
                                                                       int maxMessages) {
        return tryReserveConnectorDelivery(channel,
                                           maxMessages,
                                           dispatcher(channel).timeoutNanos(),
                                           ignored -> { });
    }

    int maxDeliveryMessages(String channel) {
        MessagingExecutionConfig config = dispatcher(channel).config;
        return Math.min(config.maxInFlightMessages(), config.maxPendingMessages());
    }

    Optional<Duration> admissionTimeout(String channel) {
        return dispatcher(channel).config.admissionTimeout();
    }

    void runWithChannelAdmissionLock(String channel, Runnable action) {
        dispatcher(channel).runWithAdmissionLock(action);
    }

    void runWithDispatchThreadRegistryLock(Runnable action) {
        dispatchThreadsLock.lock();
        try {
            action.run();
        } finally {
            dispatchThreadsLock.unlock();
        }
    }

    SourceTask startSource(String name, Runnable source) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(source);
        sourceThreadsLock.lock();
        try {
            if (!accepting.get() || closed.get()) {
                throw rejected(name,
                               MessagingRejectedException.Reason.SHUTDOWN,
                               "Messaging runtime is shutting down");
            }

            SourceTask sourceTask = new SourceTask(name, source);
            Thread thread = sourceThreadFactory.newThread(() -> {
                Throwable failure = null;
                try {
                    source.run();
                } catch (RuntimeException | Error t) {
                    failure = t;
                    throw t;
                } finally {
                    sourceThreads.remove(Thread.currentThread());
                    sourceTask.complete(failure);
                }
            });
            sourceTask.thread(thread);
            sourceThreads.add(thread);
            try {
                thread.start();
            } catch (RuntimeException | Error e) {
                sourceThreads.remove(thread);
                sourceTask.complete(e);
                throw e;
            }
            return sourceTask;
        } finally {
            sourceThreadsLock.unlock();
        }
    }

    Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    boolean isCurrentDeliveryOrSourceThread() {
        DeliveryContext delivery = CURRENT_DELIVERY.get();
        return (delivery != null && delivery.path().stream().anyMatch(node -> node.owner() == this))
                || sourceThreads.contains(Thread.currentThread());
    }

    void beginDrain() {
        if (!accepting.compareAndSet(true, false)) {
            return;
        }
        dispatchers.values().forEach(ChannelDispatcher::beginDrain);
    }

    boolean awaitDrained(Duration timeout) {
        Objects.requireNonNull(timeout);
        long deadline = saturatedAdd(System.nanoTime(), timeout.toNanos());
        for (ChannelDispatcher dispatcher : dispatchers.values()) {
            if (!dispatcher.awaitDrained(deadline)) {
                return false;
            }
        }
        return awaitSourceTermination(deadline);
    }

    void forceShutdown() {
        accepting.set(false);
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        dispatchers.values().forEach(ChannelDispatcher::close);
        sourceThreadsLock.lock();
        try {
            sourceThreads.forEach(Thread::interrupt);
        } finally {
            sourceThreadsLock.unlock();
        }
    }

    @Override
    public void close() {
        forceShutdown();
        if (!awaitTermination(shutdownTimeout)) {
            int remaining = sourceThreads.size() + dispatchThreads.size();
            LOGGER.log(System.Logger.Level.ERROR,
                       "Messaging shutdown timed out after " + shutdownTimeout
                               + "; " + remaining + " task(s) remain active");
        }
    }

    private ChannelDispatcher dispatcher(String channel) {
        ChannelDispatcher dispatcher = dispatchers.get(channel);
        if (dispatcher == null) {
            throw new MessagingException("Unknown messaging channel " + channel);
        }
        return dispatcher;
    }

    private int connectorReservationMessages(String channel, int maxMessages) {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than zero");
        }
        ChannelDispatcher dispatcher = dispatcher(channel);
        dispatcher.validateMessageCount(maxMessages);
        dispatcher.validatePendingMessageCount(maxMessages);
        return maxMessages;
    }

    private void rejectConnectorReservationFromDispatch() {
        if (CURRENT_DELIVERY.get() != null) {
            throw new MessagingException("A connector delivery cannot be reserved from messaging dispatch");
        }
    }

    private void awaitCaller(DeliveryTask task) {
        try {
            task.await();
        } catch (InterruptedException e) {
            task.cancel(MessagingRejectedException.Reason.CANCELLED,
                        "Messaging delivery caller was interrupted");
            Thread.currentThread().interrupt();
            throw new MessagingRejectedException(task.channel(),
                                                 MessagingRejectedException.Reason.CANCELLED,
                                                 "Interrupted while waiting for messaging delivery on channel "
                                                         + task.channel(),
                                                 e);
        }
    }

    private Thread startDispatch(DeliveryTask task) {
        Thread thread = dispatchThreadFactory.newThread(() -> {
            CURRENT_DELIVERY.set(task.context());
            Throwable failure = null;
            try {
                task.action().run();
            } catch (Throwable t) {
                failure = t;
            } finally {
                CURRENT_DELIVERY.remove();
                try {
                    task.finished(failure);
                } finally {
                    dispatchThreads.remove(Thread.currentThread());
                }
            }
        });
        task.thread(thread);
        dispatchThreads.add(thread);
        try {
            thread.start();
            return thread;
        } catch (RuntimeException | Error e) {
            dispatchThreads.remove(thread);
            task.thread(null);
            throw e;
        }
    }

    boolean startCleanup(Runnable cleanup) {
        dispatchThreadsLock.lock();
        try {
            if (closed.get()) {
                return false;
            }
            Thread thread = cleanupThreadFactory.newThread(() -> {
                try {
                    cleanup.run();
                } finally {
                    dispatchThreads.remove(Thread.currentThread());
                }
            });
            dispatchThreads.add(thread);
            try {
                thread.start();
                return true;
            } catch (RuntimeException | Error e) {
                dispatchThreads.remove(thread);
                throw e;
            }
        } finally {
            dispatchThreadsLock.unlock();
        }
    }

    boolean awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        long deadline = saturatedAdd(System.nanoTime(), timeout.toNanos());
        List<Thread> tasks = new ArrayList<>(sourceThreads.size() + dispatchThreads.size());
        sourceThreadsLock.lock();
        try {
            tasks.addAll(sourceThreads);
        } finally {
            sourceThreadsLock.unlock();
        }
        dispatchThreadsLock.lock();
        try {
            tasks.addAll(dispatchThreads);
        } finally {
            dispatchThreadsLock.unlock();
        }
        for (Thread task : tasks) {
            if (task == Thread.currentThread()) {
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                task.join(Duration.ofNanos(remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(System.Logger.Level.WARNING,
                           "Interrupted while waiting for messaging tasks to stop",
                           e);
                return false;
            }
        }
        return sourceThreads.isEmpty() && dispatchThreads.isEmpty();
    }

    private boolean awaitSourceTermination(long deadline) {
        List<Thread> tasks = List.copyOf(sourceThreads);
        for (Thread task : tasks) {
            if (task == Thread.currentThread()) {
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            try {
                task.join(Duration.ofNanos(remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return sourceThreads.isEmpty()
                || sourceThreads.size() == 1 && sourceThreads.contains(Thread.currentThread());
    }

    private static ThreadFactory virtualThreadFactory(String prefix, String failureMessage) {
        AtomicLong sequence = new AtomicLong();
        return runnable -> Thread.ofVirtual()
                .name(prefix + sequence.incrementAndGet())
                .inheritInheritableThreadLocals(false)
                .uncaughtExceptionHandler((thread, throwable) -> LOGGER.log(System.Logger.Level.ERROR,
                                                                            failureMessage,
                                                                            throwable))
                .unstarted(runnable);
    }

    private static long saturatedAdd(long first, long second) {
        long result = first + second;
        if (((first ^ result) & (second ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private MessagingRejectedException rejected(String channel,
                                                 MessagingRejectedException.Reason reason,
                                                 String message) {
        if (reason == MessagingRejectedException.Reason.SHUTDOWN) {
            return new RuntimeShutdownException(this, channel, message);
        }
        return new MessagingRejectedException(channel, reason, message);
    }

    static boolean isPreDispatchRejection(RuntimeException failure) {
        return failure instanceof PreDispatchRejectedException;
    }

    static void ensureCurrentDeliveryActive() {
        DeliveryContext context = CURRENT_DELIVERY.get();
        if (context != null) {
            context.ensureActive();
        }
    }

    private static boolean canMarkNotAttempted(MessagingRejectedException failure) {
        return switch (failure.reason()) {
        case OVERSIZED, SATURATED -> true;
        case TIMEOUT, SHUTDOWN, CANCELLED -> false;
        };
    }

    boolean ownsShutdownRejection(Throwable failure) {
        return failure instanceof RuntimeShutdownException shutdown && shutdown.owner == this;
    }

    private final class ChannelDispatcher {
        private final String channel;
        private final MessagingExecutionConfig config;
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition changed = lock.newCondition();
        private final Semaphore pendingAdmissions;
        private final Deque<Object> admissionOrder = new ArrayDeque<>();
        private final Deque<Object> pendingReservationOrder = new ArrayDeque<>();
        private final Deque<DeliveryTask> queue = new ArrayDeque<>();
        private final Set<DeliveryTask> retained = new LinkedHashSet<>();
        private final Set<DeliveryReservation> reservations = new LinkedHashSet<>();
        private DeliveryTask active;
        private long inFlightMessages;
        private long pendingMessages;
        private boolean dispatcherClosed;

        private ChannelDispatcher(String channel, MessagingExecutionConfig config) {
            this.channel = channel;
            this.config = config;
            this.pendingAdmissions = new Semaphore(config.maxPendingAdmissions(), true);
        }

        private DeliveryTask submit(int messageCount,
                                    List<DeliveryNode> parentPath,
                                    boolean connectorLease,
                                    AdmissionMode admissionMode,
                                    MessageBatch<?> connectorBatch,
                                    Runnable action) {
            validateMessageCount(messageCount);
            DeliveryTask task = new DeliveryTask(this,
                                                 messageCount,
                                                 new DeliveryContext(DeliveryEngine.this,
                                                                     channel,
                                                                     parentPath,
                                                                     connectorLease,
                                                                     connectorBatch),
                                                 connectorLease,
                                                 action);
            if (admissionMode == AdmissionMode.NESTED || admissionMode == AdmissionMode.TRY) {
                if (!lock.tryLock()) {
                    return null;
                }
                try {
                    if (admissionMode == AdmissionMode.NESTED) {
                        rejectIfForced();
                    } else {
                        rejectIfNotAccepting();
                    }
                    boolean admissible = admissionOrder.isEmpty()
                            && (admissionMode == AdmissionMode.NESTED
                                    ? canStartImmediately(messageCount)
                                    : canAdmit(messageCount));
                    if (!admissible) {
                        return null;
                    }
                    admit(task);
                    return task;
                } finally {
                    lock.unlock();
                }
            }

            DeliveryTask immediate = tryImmediateAdmission(task);
            if (immediate != null) {
                return immediate;
            }

            Object admissionToken = null;
            boolean pendingReserved = false;
            if (!pendingAdmissions.tryAcquire()) {
                immediate = tryImmediateAdmission(task);
                if (immediate != null) {
                    return immediate;
                }
                throw pendingSaturated("Messaging pending-admission limit reached");
            }
            try {
                // Parking for this mutex would retain the caller's delivery before its message count is reserved.
                if (!lock.tryLock()) {
                    throw pendingSaturated("Messaging dispatcher is busy");
                }
                try {
                    rejectIfNotAccepting();
                    if (admissionOrder.isEmpty() && canAdmit(messageCount)) {
                        admit(task);
                        return task;
                    }
                    if (!canReservePending(messageCount)) {
                        throw pendingSaturated("Messaging pending message limit reached");
                    }
                    reservePending(messageCount);
                    pendingReserved = true;
                    admissionToken = new Object();
                    admissionOrder.addLast(admissionToken);
                    long remaining = config.admissionTimeout()
                            .map(Duration::toNanos)
                            .orElse(Long.MAX_VALUE);
                    while (true) {
                        rejectIfNotAccepting();
                        if (admissionOrder.peekFirst() == admissionToken && canAdmit(messageCount)) {
                            admissionOrder.removeFirst();
                            releasePending(messageCount);
                            pendingReserved = false;
                            admit(task);
                            changed.signalAll();
                            return task;
                        }
                        if (remaining == Long.MAX_VALUE) {
                            changed.await();
                        } else {
                            if (remaining <= 0) {
                                throw rejected(channel,
                                               MessagingRejectedException.Reason.TIMEOUT,
                                               "Messaging admission timed out on channel " + channel);
                            }
                            remaining = changed.awaitNanos(remaining);
                        }
                    }
                } finally {
                    if (admissionToken != null && admissionOrder.remove(admissionToken)) {
                        changed.signalAll();
                    }
                    if (pendingReserved) {
                        releasePending(messageCount);
                        changed.signalAll();
                    }
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingRejectedException(channel,
                                                     MessagingRejectedException.Reason.CANCELLED,
                                                     "Interrupted while waiting for messaging admission on channel "
                                                             + channel,
                                                     e);
            } finally {
                pendingAdmissions.release();
            }
        }

        private DeliveryTask tryImmediateAdmission(DeliveryTask task) {
            if (!lock.tryLock()) {
                return null;
            }
            try {
                rejectIfNotAccepting();
                if (!admissionOrder.isEmpty() || !canAdmit(task.messageCount())) {
                    return null;
                }
                admit(task);
                return task;
            } finally {
                lock.unlock();
            }
        }

        private DeliveryReservation reserveConnectorDelivery(int messageCount,
                                                             AdmissionMode admissionMode,
                                                             long initialCapacityWaitNanos,
                                                             Consumer<MessageBatch<?>> processor) {
            validatePendingMessageCount(messageCount);
            if (!pendingAdmissions.tryAcquire()) {
                if (admissionMode == AdmissionMode.TRY) {
                    return null;
                }
                throw pendingSaturated("Messaging pending-admission limit reached");
            }

            boolean transferPermit = false;
            Object reservationToken = null;
            try {
                if (admissionMode == AdmissionMode.TRY) {
                    if (!lock.tryLock()) {
                        return null;
                    }
                } else {
                    // Parking here would retain the requested transport capacity before it is accounted as pending.
                    if (!lock.tryLock()) {
                        throw pendingSaturated("Messaging dispatcher is busy");
                    }
                }
                try {
                    rejectIfNotAccepting();
                    if (admissionMode == AdmissionMode.TRY) {
                        if (!pendingReservationOrder.isEmpty() || !canReservePending(messageCount)) {
                            return null;
                        }
                        DeliveryReservation result = createReservation(messageCount,
                                                                       initialCapacityWaitNanos,
                                                                       processor);
                        transferPermit = true;
                        return result;
                    }

                    reservationToken = new Object();
                    pendingReservationOrder.addLast(reservationToken);
                    long remaining = initialCapacityWaitNanos < 0 ? timeoutNanos() : initialCapacityWaitNanos;
                    while (true) {
                        rejectIfNotAccepting();
                        if (pendingReservationOrder.peekFirst() == reservationToken
                                && canReservePending(messageCount)) {
                            pendingReservationOrder.removeFirst();
                            DeliveryReservation result = createReservation(messageCount, remaining, processor);
                            transferPermit = true;
                            changed.signalAll();
                            return result;
                        }
                        remaining = awaitCapacity(remaining,
                                                  "Messaging delivery reservation timed out on channel " + channel);
                    }
                } finally {
                    if (reservationToken != null && pendingReservationOrder.remove(reservationToken)) {
                        changed.signalAll();
                    }
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingRejectedException(
                        channel,
                        MessagingRejectedException.Reason.CANCELLED,
                        "Interrupted while waiting for messaging delivery reservation on channel " + channel,
                        e);
            } finally {
                if (!transferPermit) {
                    pendingAdmissions.release();
                }
            }
        }

        private DeliveryReservation createReservation(int messageCount,
                                                      long remainingCapacityWaitNanos,
                                                      Consumer<MessageBatch<?>> processor) {
            reservePending(messageCount);
            DeliveryReservation result = new DeliveryReservation(this,
                                                                 messageCount,
                                                                 remainingCapacityWaitNanos,
                                                                 processor);
            reservations.add(result);
            return result;
        }

        private DeliveryTask startReservation(DeliveryReservation reservation,
                                              int actualMessages,
                                              MessageBatch<?> connectorBatch,
                                              Runnable action,
                                              AdmissionMode admissionMode) {
            validateReservationActual(reservation, actualMessages);
            DeliveryTask task = new DeliveryTask(this,
                                                 actualMessages,
                                                 new DeliveryContext(DeliveryEngine.this,
                                                                     channel,
                                                                     List.of(),
                                                                     true,
                                                                     connectorBatch),
                                                 true,
                                                 action);
            Object admissionToken = null;
            try {
                if (admissionMode == AdmissionMode.TRY) {
                    if (!lock.tryLock()) {
                        return null;
                    }
                } else {
                    lockForReservationStart(reservation);
                }
                try {
                    reservation.requireOpen();
                    rejectIfForced();
                    if (admissionMode == AdmissionMode.TRY) {
                        if (!admissionOrder.isEmpty() || !canAdmit(actualMessages)) {
                            return null;
                        }
                        reservation.state.set(ReservationState.STARTING);
                    } else if (!admissionOrder.isEmpty() || !canAdmit(actualMessages)) {
                        reservation.state.set(ReservationState.STARTING);
                        admissionToken = new Object();
                        reservation.waitingToken = admissionToken;
                        admissionOrder.addLast(admissionToken);
                        while (true) {
                            reservation.requireStarting();
                            rejectIfForced();
                            if (admissionOrder.peekFirst() == admissionToken && canAdmit(actualMessages)) {
                                admissionOrder.removeFirst();
                                reservation.waitingToken = null;
                                break;
                            }
                            reservation.remainingCapacityWaitNanos = awaitCapacity(
                                    reservation.remainingCapacityWaitNanos,
                                    "Messaging delivery reservation start timed out on channel " + channel);
                        }
                    } else {
                        reservation.state.set(ReservationState.STARTING);
                    }

                    reservations.remove(reservation);
                    releasePending(reservation.reservedMessages);
                    pendingAdmissions.release();
                    reservation.state.set(ReservationState.STARTED);
                    try {
                        admit(task);
                    } catch (RuntimeException | Error e) {
                        reservation.state.set(ReservationState.CLOSED);
                        changed.signalAll();
                        throw e;
                    }
                    changed.signalAll();
                    return task;
                } finally {
                    if (admissionToken != null && admissionOrder.remove(admissionToken)) {
                        reservation.waitingToken = null;
                        changed.signalAll();
                    }
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                closeReservationWithoutWaiting(reservation, ReservationState.CLOSED);
                Thread.currentThread().interrupt();
                throw new MessagingRejectedException(
                        channel,
                        MessagingRejectedException.Reason.CANCELLED,
                        "Interrupted while waiting to start messaging delivery on channel " + channel,
                        e);
            } catch (MessagingRejectedException e) {
                if (e.reason() == MessagingRejectedException.Reason.TIMEOUT) {
                    closeReservationWithoutWaiting(reservation, ReservationState.CLOSED);
                } else if (e.reason() == MessagingRejectedException.Reason.SHUTDOWN
                        || e.reason() == MessagingRejectedException.Reason.CANCELLED) {
                    closeReservation(reservation,
                                     e.reason() == MessagingRejectedException.Reason.SHUTDOWN
                                             ? ReservationState.SHUTDOWN
                                             : ReservationState.CLOSED);
                }
                throw e;
            }
        }

        private void lockForReservationStart(DeliveryReservation reservation) throws InterruptedException {
            long remaining = reservation.remainingCapacityWaitNanos;
            if (remaining == Long.MAX_VALUE) {
                lock.lockInterruptibly();
                return;
            }

            long started = System.nanoTime();
            boolean acquired = lock.tryLock(Math.max(0, remaining), TimeUnit.NANOSECONDS);
            long elapsed = Math.max(0, System.nanoTime() - started);
            reservation.remainingCapacityWaitNanos = remaining <= elapsed ? 0 : remaining - elapsed;
            if (!acquired) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.TIMEOUT,
                               "Messaging delivery reservation start timed out on channel " + channel);
            }
        }

        private void validateReservationActual(DeliveryReservation reservation, int actualMessages) {
            reservation.requireOpen();
            validateMessageCount(actualMessages);
            if (actualMessages > reservation.reservedMessages) {
                closeReservation(reservation, ReservationState.CLOSED);
                throw rejected(channel,
                               MessagingRejectedException.Reason.OVERSIZED,
                               "Connector delivery exceeds its pending reservation on channel " + channel);
            }
        }

        private void closeReservation(DeliveryReservation reservation, ReservationState targetState) {
            if (!reservation.canTransitionToTerminal()) {
                return;
            }
            lock.lock();
            try {
                if (!reservation.transitionToTerminal(targetState)) {
                    return;
                }
                cleanupReservationLocked(reservation);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void closeReservationWithoutWaiting(DeliveryReservation reservation, ReservationState targetState) {
            if (!reservation.transitionToTerminal(targetState)) {
                return;
            }
            if (lock.tryLock()) {
                try {
                    cleanupReservationLocked(reservation);
                    changed.signalAll();
                } finally {
                    lock.unlock();
                }
                return;
            }

            try {
                startCleanup(() -> cleanupReservation(reservation));
            } catch (RuntimeException | Error e) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Could not start deferred messaging reservation cleanup; cleaning up synchronously",
                           e);
                cleanupReservation(reservation);
            }
        }

        private void cleanupReservation(DeliveryReservation reservation) {
            lock.lock();
            try {
                cleanupReservationLocked(reservation);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void cleanupReservationLocked(DeliveryReservation reservation) {
            if (reservation.waitingToken != null && admissionOrder.remove(reservation.waitingToken)) {
                reservation.waitingToken = null;
            }
            if (reservations.remove(reservation)) {
                releasePending(reservation.reservedMessages);
                pendingAdmissions.release();
            }
        }

        private long timeoutNanos() {
            return config.admissionTimeout().map(Duration::toNanos).orElse(Long.MAX_VALUE);
        }

        private long awaitCapacity(long remaining, String timeoutMessage) throws InterruptedException {
            if (remaining <= 0) {
                throw rejected(channel, MessagingRejectedException.Reason.TIMEOUT, timeoutMessage);
            }
            long updated = changed.awaitNanos(remaining);
            return remaining == Long.MAX_VALUE ? Long.MAX_VALUE : updated;
        }

        private void admit(DeliveryTask task) {
            retained.add(task);
            reserve(task.messageCount());
            if (active == null && queue.isEmpty()) {
                active = task;
                try {
                    startDispatch(task);
                } catch (RuntimeException | Error e) {
                    active = null;
                    task.executionFinished = true;
                    release(task);
                    changed.signalAll();
                    throw e;
                }
            } else {
                queue.addLast(task);
            }
        }

        private boolean canAdmit(int messageCount) {
            boolean executionCapacity = active == null || queue.size() < config.queueCapacity();
            return executionCapacity
                    && messageCount <= config.maxInFlightMessages() - inFlightMessages;
        }

        private boolean canStartImmediately(int messageCount) {
            return queue.isEmpty()
                    && active == null
                    && messageCount <= config.maxInFlightMessages() - inFlightMessages;
        }

        private boolean canReservePending(int messageCount) {
            return messageCount <= config.maxPendingMessages() - pendingMessages;
        }

        private void validateMessageCount(int messageCount) {
            if (messageCount > config.maxInFlightMessages()) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.OVERSIZED,
                               "Delivery contains " + messageCount
                                       + " messages, exceeding channel " + channel
                                       + " limit " + config.maxInFlightMessages());
            }
        }

        private void validatePendingMessageCount(int messageCount) {
            if (messageCount > config.maxPendingMessages()) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.OVERSIZED,
                               "Delivery reservation contains " + messageCount
                                       + " messages, exceeding channel " + channel
                                       + " pending limit " + config.maxPendingMessages());
            }
        }

        private MessagingRejectedException pendingSaturated(String message) {
            return rejected(channel,
                            MessagingRejectedException.Reason.SATURATED,
                            message + " on channel " + channel);
        }

        private void runWithAdmissionLock(Runnable action) {
            lock.lock();
            try {
                action.run();
            } finally {
                lock.unlock();
            }
        }

        private void reserve(int messageCount) {
            inFlightMessages += messageCount;
        }

        private void reservePending(int messageCount) {
            pendingMessages += messageCount;
        }

        private void releasePending(int messageCount) {
            pendingMessages -= messageCount;
        }

        private void release(DeliveryTask task) {
            if (retained.remove(task)) {
                inFlightMessages -= task.messageCount();
            }
        }

        private void finished(DeliveryTask task, Throwable failure) {
            Throwable completionFailure;
            lock.lock();
            try {
                if (active != task) {
                    return;
                }
                active = null;
                task.executionFinished = true;
                completionFailure = task.cancellationFailure == null ? failure : task.cancellationFailure;
                if (!task.connectorLease || task.releaseRequested) {
                    release(task);
                }
                while (!closed.get()
                        && !dispatcherClosed
                        && active == null
                        && !queue.isEmpty()) {
                    DeliveryTask next = queue.removeFirst();
                    active = next;
                    try {
                        startDispatch(next);
                    } catch (RuntimeException | Error e) {
                        active = null;
                        next.executionFinished = true;
                        release(next);
                        next.complete(e);
                    }
                }
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            task.complete(completionFailure);
        }

        private void cancel(DeliveryTask task,
                            MessagingRejectedException.Reason reason,
                            String message) {
            Thread activeThread = null;
            boolean complete = false;
            lock.lock();
            try {
                if (queue.remove(task)) {
                    task.requestCancellation(reason, message);
                    task.executionFinished = true;
                    if (!task.connectorLease) {
                        release(task);
                    }
                    complete = true;
                    changed.signalAll();
                } else if (active == task) {
                    task.requestCancellation(reason, message);
                    activeThread = task.thread();
                }
            } finally {
                lock.unlock();
            }
            if (complete) {
                task.complete(rejected(channel, reason, message));
            }
            if (activeThread != null) {
                activeThread.interrupt();
            }
        }

        private void releaseConnector(DeliveryTask task) {
            Thread activeThread = null;
            boolean complete = false;
            lock.lock();
            try {
                task.releaseRequested = true;
                if (queue.remove(task)) {
                    task.requestCancellation(MessagingRejectedException.Reason.CANCELLED,
                                             "Messaging delivery lease was released before processing started");
                    task.executionFinished = true;
                    release(task);
                    complete = true;
                } else if (active == task) {
                    task.requestCancellation(MessagingRejectedException.Reason.CANCELLED,
                                             "Messaging delivery lease was released before processing completed");
                    activeThread = task.thread();
                } else if (task.executionFinished) {
                    release(task);
                }
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            if (complete) {
                task.complete(rejected(channel,
                                       MessagingRejectedException.Reason.CANCELLED,
                                       "Messaging delivery lease was released before processing started"));
            }
            if (activeThread != null) {
                activeThread.interrupt();
            }
        }

        private void beginDrain() {
            lock.lock();
            try {
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private boolean awaitDrained(long deadline) {
            try {
                lock.lockInterruptibly();
                try {
                    while (!isDrained()) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            return false;
                        }
                        long updated = changed.awaitNanos(remaining);
                        if (updated <= 0 && !isDrained()) {
                            return false;
                        }
                    }
                    return true;
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean isDrained() {
            return admissionOrder.isEmpty()
                    && pendingReservationOrder.isEmpty()
                    && queue.isEmpty()
                    && active == null
                    && retained.isEmpty()
                    && reservations.isEmpty();
        }

        private void close() {
            List<DeliveryTask> queued;
            Thread running;
            lock.lock();
            try {
                dispatcherClosed = true;
                for (DeliveryReservation reservation : List.copyOf(reservations)) {
                    reservation.state.set(ReservationState.SHUTDOWN);
                    if (reservation.waitingToken != null) {
                        admissionOrder.remove(reservation.waitingToken);
                        reservation.waitingToken = null;
                    }
                    releasePending(reservation.reservedMessages);
                    pendingAdmissions.release();
                }
                reservations.clear();
                queued = new ArrayList<>(queue);
                queue.clear();
                for (DeliveryTask task : queued) {
                    task.requestCancellation(MessagingRejectedException.Reason.SHUTDOWN,
                                             "Messaging runtime is shutting down");
                    task.executionFinished = true;
                    release(task);
                }
                if (active != null) {
                    active.releaseRequested = true;
                    active.requestCancellation(MessagingRejectedException.Reason.SHUTDOWN,
                                               "Messaging runtime is shutting down");
                }
                List<DeliveryTask> completedLeases = retained.stream()
                        .filter(task -> task != active && !queue.contains(task))
                        .toList();
                completedLeases.forEach(this::release);
                running = active == null ? null : active.thread();
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            for (DeliveryTask task : queued) {
                task.complete(rejected(channel,
                                       MessagingRejectedException.Reason.SHUTDOWN,
                                       "Messaging runtime is shutting down"));
            }
            if (running != null) {
                running.interrupt();
            }
        }

        private void rejectIfNotAccepting() {
            if (!accepting.get()) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.SHUTDOWN,
                               "Messaging runtime is draining");
            }
            rejectIfForced();
        }

        private void rejectIfForced() {
            if (dispatcherClosed || closed.get()) {
                throw rejected(channel,
                               MessagingRejectedException.Reason.SHUTDOWN,
                               "Messaging runtime is shutting down");
            }
        }
    }

    final class SourceTask {
        private final String name;
        private final Runnable source;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile Thread thread;

        private SourceTask(String name, Runnable source) {
            this.name = name;
            this.source = source;
        }

        String name() {
            return name;
        }

        Runnable source() {
            return source;
        }

        Optional<Throwable> failure() {
            return Optional.ofNullable(failure.get());
        }

        void onCompletion(Consumer<Optional<Throwable>> listener) {
            completion.whenComplete((ignored, throwable) -> listener.accept(failure()));
        }

        boolean await(Duration timeout) throws InterruptedException {
            try {
                completion.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                return true;
            } catch (ExecutionException e) {
                return true;
            } catch (TimeoutException e) {
                return false;
            }
        }

        void interrupt() {
            Thread current = thread;
            if (current != null) {
                current.interrupt();
            }
        }

        private void thread(Thread thread) {
            this.thread = thread;
        }

        private void complete(Throwable failure) {
            if (failure == null) {
                completion.complete(null);
            } else {
                this.failure.compareAndSet(null, failure);
                completion.completeExceptionally(failure);
            }
        }
    }

    private static final class RuntimeShutdownException extends MessagingRejectedException {
        private final DeliveryEngine owner;

        private RuntimeShutdownException(DeliveryEngine owner, String channel, String message) {
            super(channel, Reason.SHUTDOWN, message);
            this.owner = owner;
        }
    }

    private static final class PreDispatchRejectedException extends MessagingRejectedException {
        private PreDispatchRejectedException(MessagingRejectedException failure) {
            super(failure.channel(), failure.reason(), failure.getMessage(), failure);
        }
    }

    private final class DeliveryReservation implements ConnectorDeliveryReservation {
        private final ChannelDispatcher dispatcher;
        private final int reservedMessages;
        private final Consumer<MessageBatch<?>> processor;
        private final AtomicBoolean startClaimed = new AtomicBoolean();
        private final AtomicReference<ReservationState> state = new AtomicReference<>(ReservationState.OPEN);
        private long remainingCapacityWaitNanos;
        private long tryStartDeadline = Long.MIN_VALUE;
        private Object waitingToken;

        private DeliveryReservation(ChannelDispatcher dispatcher,
                                    int reservedMessages,
                                    long remainingCapacityWaitNanos,
                                    Consumer<MessageBatch<?>> processor) {
            this.dispatcher = dispatcher;
            this.reservedMessages = reservedMessages;
            this.remainingCapacityWaitNanos = remainingCapacityWaitNanos;
            this.processor = processor;
        }

        @Override
        public ConnectorDelivery start(MessageBatch<?> batch) {
            claimStart();
            try {
                Objects.requireNonNull(batch);
                updateTryStartBudget();
                return dispatcher.startReservation(this,
                                                   batch.size(),
                                                   batch,
                                                   () -> processor.accept(batch),
                                                   AdmissionMode.WAIT);
            } catch (RuntimeException | Error e) {
                dispatcher.closeReservation(this, ReservationState.CLOSED);
                throw e;
            }
        }

        @Override
        public Optional<ConnectorDelivery> tryStart(MessageBatch<?> batch) {
            claimStart();
            try {
                Objects.requireNonNull(batch);
                long attemptStarted = System.nanoTime();
                updateTryStartBudget();
                DeliveryTask task = dispatcher.startReservation(this,
                                                                batch.size(),
                                                                batch,
                                                                () -> processor.accept(batch),
                                                                AdmissionMode.TRY);
                if (task == null) {
                    beginTryStartBudget(attemptStarted);
                    updateTryStartBudget();
                    startClaimed.set(false);
                }
                return Optional.ofNullable(task);
            } catch (RuntimeException | Error e) {
                dispatcher.closeReservation(this, ReservationState.CLOSED);
                throw e;
            }
        }

        @Override
        public void close() {
            dispatcher.closeReservation(this, ReservationState.CLOSED);
        }

        private void requireOpen() {
            ReservationState current = state.get();
            switch (current) {
            case OPEN:
                return;
            case STARTED:
                throw new IllegalStateException("Connector delivery reservation was already started");
            case STARTING:
                throw new IllegalStateException("Connector delivery reservation is already being started");
            case CLOSED:
                throw rejected(dispatcher.channel,
                               MessagingRejectedException.Reason.CANCELLED,
                               "Connector delivery reservation is closed");
            case SHUTDOWN:
                throw rejected(dispatcher.channel,
                               MessagingRejectedException.Reason.SHUTDOWN,
                               "Messaging runtime is shutting down");
            default:
                throw new IllegalStateException("Unsupported connector delivery reservation state: " + current);
            }
        }

        private void claimStart() {
            requireOpen();
            if (!startClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("Connector delivery reservation is already being started");
            }
            try {
                requireOpen();
            } catch (RuntimeException | Error e) {
                startClaimed.set(false);
                throw e;
            }
        }

        private void updateTryStartBudget() {
            if (tryStartDeadline == Long.MIN_VALUE || remainingCapacityWaitNanos == Long.MAX_VALUE) {
                return;
            }
            long remaining = tryStartDeadline - System.nanoTime();
            if (remaining <= 0) {
                remainingCapacityWaitNanos = 0;
                throw rejected(dispatcher.channel,
                               MessagingRejectedException.Reason.TIMEOUT,
                               "Messaging delivery reservation start timed out on channel " + dispatcher.channel);
            }
            remainingCapacityWaitNanos = remaining;
        }

        private void beginTryStartBudget(long attemptStarted) {
            if (tryStartDeadline == Long.MIN_VALUE && remainingCapacityWaitNanos != Long.MAX_VALUE) {
                tryStartDeadline = saturatedAdd(attemptStarted, remainingCapacityWaitNanos);
                remainingCapacityWaitNanos = Math.max(0, tryStartDeadline - System.nanoTime());
            }
        }

        private void requireStarting() {
            if (state.get() == ReservationState.STARTING) {
                return;
            }
            requireOpen();
        }

        private boolean canTransitionToTerminal() {
            ReservationState current = state.get();
            return current == ReservationState.OPEN || current == ReservationState.STARTING;
        }

        private boolean transitionToTerminal(ReservationState targetState) {
            while (true) {
                ReservationState current = state.get();
                if (current != ReservationState.OPEN && current != ReservationState.STARTING) {
                    return false;
                }
                if (state.compareAndSet(current, targetState)) {
                    return true;
                }
            }
        }
    }

    private final class DeliveryTask implements ConnectorDelivery {
        private final ChannelDispatcher dispatcher;
        private final int messageCount;
        private final DeliveryContext context;
        private final boolean connectorLease;
        private final Runnable action;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile Thread thread;
        private boolean executionFinished;
        private boolean releaseRequested;
        private MessagingRejectedException cancellationFailure;

        private DeliveryTask(ChannelDispatcher dispatcher,
                             int messageCount,
                             DeliveryContext context,
                             boolean connectorLease,
                             Runnable action) {
            this.dispatcher = dispatcher;
            this.messageCount = messageCount;
            this.context = context;
            this.connectorLease = connectorLease;
            this.action = action;
        }

        @Override
        public boolean isDone() {
            return completion.isDone();
        }

        @Override
        public boolean isCurrentThread() {
            return thread == Thread.currentThread();
        }

        @Override
        public void await() throws InterruptedException {
            try {
                completion.get();
            } catch (ExecutionException e) {
                rethrow(e);
            }
        }

        @Override
        public boolean await(Duration timeout) throws InterruptedException {
            try {
                completion.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                return true;
            } catch (ExecutionException e) {
                rethrow(e);
                return true;
            } catch (TimeoutException e) {
                return false;
            }
        }

        @Override
        public void cancel() {
            cancel(MessagingRejectedException.Reason.CANCELLED,
                   "Messaging delivery was cancelled");
        }

        @Override
        public void close() {
            if (!connectorLease) {
                return;
            }
            dispatcher.releaseConnector(this);
        }

        private void cancel(MessagingRejectedException.Reason reason, String message) {
            dispatcher.cancel(this, reason, message);
        }

        private void finished(Throwable failure) {
            dispatcher.finished(this, failure);
        }

        private void complete(Throwable failure) {
            if (failure == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(failure);
            }
        }

        private void requestCancellation(MessagingRejectedException.Reason reason, String message) {
            if (cancellationFailure == null) {
                cancellationFailure = rejected(channel(), reason, message);
                context.cancel(cancellationFailure);
            }
        }

        private void thread(Thread thread) {
            this.thread = thread;
        }

        private Thread thread() {
            return thread;
        }

        private String channel() {
            return dispatcher.channel;
        }

        private int messageCount() {
            return messageCount;
        }

        private DeliveryContext context() {
            return context;
        }

        private Runnable action() {
            return action;
        }

        private void rethrow(ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new MessagingException("Messaging delivery failed on channel " + channel(), cause);
        }
    }

    private static final class DeliveryContext {
        private final DeliveryEngine owner;
        private final String channel;
        private final List<DeliveryNode> path;
        private final boolean connectorLease;
        private final MessageBatch<?> retainedBatch;
        private final AtomicReference<MessagingRejectedException> cancellationFailure = new AtomicReference<>();
        private int dispatchDepth;

        private DeliveryContext(DeliveryEngine owner,
                                String channel,
                                List<DeliveryNode> parentPath,
                                boolean connectorLease,
                                MessageBatch<?> retainedBatch) {
            this.owner = owner;
            this.channel = channel;
            List<DeliveryNode> path = new ArrayList<>(parentPath.size() + 1);
            path.addAll(parentPath);
            path.add(new DeliveryNode(owner, channel));
            this.path = List.copyOf(path);
            this.connectorLease = connectorLease;
            this.retainedBatch = connectorLease ? Objects.requireNonNull(retainedBatch) : null;
        }

        private boolean connectorLease(DeliveryEngine targetOwner, String targetChannel) {
            return connectorLease
                    && owner == targetOwner
                    && channel.equals(targetChannel)
                    && dispatchDepth == 0;
        }

        private void cancel(MessagingRejectedException failure) {
            cancellationFailure.compareAndSet(null, failure);
        }

        private void ensureActive() {
            MessagingRejectedException failure = cancellationFailure.get();
            if (failure != null) {
                throw failure;
            }
        }

        private void dispatchWithinLease(MessageBatch<?> batch, Runnable action) {
            if (dispatchDepth != 0) {
                throw new MessagingException("Cyclic synchronous messaging emission: "
                                                     + String.join(" -> ", pathNames()) + " -> " + channel);
            }
            if (!retains(batch)) {
                throw owner.rejected(
                        channel,
                        MessagingRejectedException.Reason.OVERSIZED,
                        "Connector emission is not part of its retained delivery lease on channel " + channel);
            }
            dispatchDepth++;
            try {
                ensureActive();
                action.run();
            } finally {
                dispatchDepth--;
            }
        }

        private boolean retains(MessageBatch<?> batch) {
            return batch.isRetainedSubsetOf(retainedBatch);
        }

        private List<DeliveryNode> path() {
            return path;
        }

        private List<String> pathNames() {
            return path.stream().map(DeliveryNode::channel).toList();
        }
    }

    private record DeliveryNode(DeliveryEngine owner, String channel) {
    }

    private enum AdmissionMode {
        WAIT,
        TRY,
        NESTED
    }

    private enum ReservationState {
        OPEN,
        STARTING,
        STARTED,
        CLOSED,
        SHUTDOWN
    }
}
