/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;
import java.util.function.Function;

import io.helidon.quic.MinimalFuture;
import io.helidon.quic.SequentialScheduler;

/**
 * Quic specifies limits on the number of uni and bidi streams that an endpoint can create.
 * This {@code StreamCreationPermit} is used to keep track of that limit and is expected to be
 * used before attempting to open a Quic stream. Either of {@link #tryAcquire()} or
 * {@link #tryAcquire(long, TimeUnit, Executor)} must be used before attempting to open a new stream. Stream
 * must only be opened if that method returns {@code true} which implies the stream creation limit
 * hasn't yet reached.
 * <p>
 * It is expected that for each of the stream types (remote uni, remote bidi, local uni
 * and local bidi) a separate instance of {@code StreamCreationPermit} will be used.
 * <p>
 * An instance of {@code StreamCreationPermit} starts with an initial limit and that limit can be
 * increased to newer higher values whenever necessary. The limit however cannot be reduced to a
 * lower value.
 * <p>
 * None of the methods, including {@link #tryAcquire(long, TimeUnit, Executor)} and {@link #tryAcquire()}
 * block the caller thread.
 */
final class StreamCreationPermit {

    private final InternalSemaphore semaphore;
    private final SequentialScheduler permitAcquisitionScheduler =
            SequentialScheduler.lockingScheduler(new TryAcquireTask());

    private final ConcurrentLinkedQueue<Waiter> acquirers = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Throwable> terminationCause = new AtomicReference<>();

    /**
     * Creates a stream creation permit with the initial maximum number of streams.
     *
     * @param initialMaxStreams the initial max streams limit
     * @throws IllegalArgumentException if {@code initialMaxStreams} is less than 0
     * @throws NullPointerException     if executor is null
     */
    StreamCreationPermit(long initialMaxStreams) {
        if (initialMaxStreams < 0) {
            throw new IllegalArgumentException("Invalid max streams limit: " + initialMaxStreams);
        }
        this.semaphore = new InternalSemaphore(initialMaxStreams);
    }

    /**
     * Attempts to increase the limit to {@code newLimit}. The limit will be atomically increased
     * to the {@code newLimit}. If the {@linkplain #currentLimit() current limit} is higher than
     * the {@code newLimit}, then the limit isn't changed and this method returns {@code false}.
     *
     * @param newLimit the new limit
     * @return true if the limit was successfully increased to {@code newLimit}, false otherwise.
     */
    boolean tryIncreaseLimitTo(long newLimit) {
        if (terminationCause.get() != null) {
            return false;
        }
        boolean increased = this.semaphore.tryIncreaseLimitTo(newLimit);
        if (increased) {
            // let any waiting acquirers attempt acquiring a permit
            permitAcquisitionScheduler.runOrSchedule();
        }
        return increased;
    }

    /**
     * Attempts to acquire a permit to open a new stream. This method does not block and returns
     * immediately. A stream should only be opened if the permit was successfully acquired.
     *
     * @return true if the permit was acquired and a new stream is allowed to be opened.
     *        false otherwise.
     */
    boolean tryAcquire() {
        if (terminationCause.get() != null) {
            return false;
        }
        boolean acquired = this.semaphore.tryAcquireShared(1) >= 0;
        if (acquired && terminationCause.get() != null) {
            releaseAcquisition();
            return false;
        }
        return acquired;
    }

    /**
     * Acquires a permit when one becomes available.
     *
     * <p>This method has no timeout. Canceling the returned future removes its pending waiter. If cancellation races
     * with acquisition, an acquired permit is released.
     *
     * @param executor executor used to complete a pending acquisition
     * @return future completed with {@code true} after acquiring a permit
     * @throws NullPointerException if {@code executor} is {@code null}
     */
    CompletableFuture<Boolean> tryAcquire(Executor executor) {
        Objects.requireNonNull(executor);
        Throwable closed = terminationCause.get();
        if (closed != null) {
            return MinimalFuture.failedMinimalFuture(closed);
        }
        if (tryAcquire()) {
            return MinimalFuture.completedMinimalFuture(true);
        }
        closed = terminationCause.get();
        if (closed != null) {
            return MinimalFuture.failedMinimalFuture(closed);
        }
        return enqueueAcquirer(MinimalFuture.create(), executor);
    }

    /**
     * Attempts to acquire a permit to open a new stream. If the permit is available then this method
     * returns immediately with a {@link CompletableFuture} whose result is {@code true}. If the
     * permit isn't currently available then this method returns a {@code CompletableFuture} which
     * completes with a result of {@code false} if no permits were available for the duration
     * represented by the {@code timeout}. If during this {@code timeout} period, a permit is
     * acquired, because of an increase in the stream limit, then the returned
     * {@code CompletableFuture} completes with a result of {@code true}.
     *
     * @param timeout  the maximum amount of time to attempt acquiring a permit, after which the
     *                {@code CompletableFuture} will complete with a result of {@code false}
     * @param unit     the timeout unit
     * @param executor the executor that will be used to asynchronously complete the
     *                returned {@code CompletableFuture} if a permit is acquired after this
     *                method has returned
     * @return a {@code CompletableFuture} whose result will be {@code true} if the permit was
     *        acquired and {@code false} otherwise
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws NullPointerException     if the {@code executor} is null
     */
    CompletableFuture<Boolean> tryAcquire(long timeout, TimeUnit unit,
                                          Executor executor) {
        Objects.requireNonNull(executor);
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid timeout: " + timeout);
        }
        Throwable closed = terminationCause.get();
        if (closed != null) {
            return MinimalFuture.failedMinimalFuture(closed);
        }
        if (tryAcquire()) {
            return MinimalFuture.completedMinimalFuture(true);
        }
        closed = terminationCause.get();
        if (closed != null) {
            return MinimalFuture.failedMinimalFuture(closed);
        }
        CompletableFuture<Boolean> future = MinimalFuture.<Boolean>create()
                .orTimeout(timeout, unit)
                .handle((acquired, t) -> {
                    if (t instanceof TimeoutException te) {
                        // timed out
                        return MinimalFuture.completedMinimalFuture(false);
                    }
                    if (t == null) {
                        // completed normally
                        return MinimalFuture.completedMinimalFuture(acquired);
                    }
                    return MinimalFuture.<Boolean>failedMinimalFuture(t);
                }).thenComposeAsync(Function.identity(), executor);
        return enqueueAcquirer(future, executor);
    }

    /**
     * {@return the current limit for stream creation}
     */
    long currentLimit() {
        return this.semaphore.currentLimit();
    }

    void terminate(Throwable cause) {
        Objects.requireNonNull(cause);
        if (terminationCause.compareAndSet(null, cause)) {
            Waiter waiter;
            while ((waiter = acquirers.poll()) != null) {
                waiter.acquirer.completeExceptionally(cause);
            }
        }
    }

    void releaseAcquisition() {
        semaphore.releaseShared(1);
        if (terminationCause.get() == null) {
            permitAcquisitionScheduler.runOrSchedule();
        }
    }

    private CompletableFuture<Boolean> enqueueAcquirer(CompletableFuture<Boolean> future, Executor executor) {
        var waiter = new Waiter(future, executor);
        this.acquirers.add(waiter);
        // If the future completes or is canceled, the Waiter should be removed from the list.
        // because this is a queue it might not be too efficient...
        future.whenComplete((r, t) -> acquirers.remove(waiter));
        Throwable closed = terminationCause.get();
        if (closed != null && acquirers.remove(waiter)) {
            future.completeExceptionally(closed);
            return future;
        }
        // if stream limit might have increased in the meantime,
        // trigger the task to have this newly registered waiter notified
        try {
            permitAcquisitionScheduler.runOrSchedule(executor);
        } catch (RejectedExecutionException e) {
            acquirers.remove(waiter);
            future.completeExceptionally(e);
        }
        return future;
    }

    private record Waiter(CompletableFuture<Boolean> acquirer,
                          Executor executor) {
        Waiter {
        }
    }

    /**
     * A {@link AbstractQueuedLongSynchronizer} whose {@linkplain #getState() state} represents
     * the number of permits that have currently been acquired. This {@code Semaphore} only
     * supports "shared" mode; i.e. exclusive mode isn't supported.
     * <p>
     * The {@code Semaphore} maintains a {@linkplain #limit limit} which represents
     * the maximum number of permits that can be acquired through an instance of this class.
     * The {@code limit} can be {@linkplain #tryIncreaseLimitTo(long) increased} but cannot be
     * reduced from the previous set limit.
     */
    private static final class InternalSemaphore extends AbstractQueuedLongSynchronizer {
        private static final long serialVersionUID = 4280985311770761500L;

        private final AtomicLong limit;

        /**
         * Creates a semaphore with the initial permit limit.
         *
         * @param initialLimit the initial limit, must be >=0
         */
        private InternalSemaphore(long initialLimit) {
            this.limit = new AtomicLong(initialLimit);
            setState(0 /* num acquired */);
        }

        /**
         * Attempts to acquire additional permits. If no permits can be acquired,
         * then this method returns -1. Upon successfully acquiring the
         * {@code additionalAcquisitions} this method returns a value {@code >=0} which represents
         * the additional number of permits that are available for acquisition.
         *
         * @param additionalAcquisitions the additional permits that are requested
         * @return -1 If no permits can be acquired. Value >=0, representing the permits that are
         *        still available for acquisition.
         */
        @Override
        protected long tryAcquireShared(long additionalAcquisitions) {
            while (true) {
                long alreadyAcquired = getState();
                long totalOnAcquisition = alreadyAcquired + additionalAcquisitions;
                long currentLimit = limit.get();
                if (totalOnAcquisition > currentLimit) {
                    return -1; // exceeds limit, so cannot acquire
                }
                long numAvailableUponAcquisition = currentLimit - totalOnAcquisition;
                if (compareAndSetState(alreadyAcquired, totalOnAcquisition)) {
                    return numAvailableUponAcquisition;
                }
            }
        }

        /**
         * Attempts to release permits.
         *
         * @param releases the number of permits to release
         * @return true if the permits were released, false otherwise
         * @throws IllegalArgumentException if the number of {@code releases} exceeds the total
         *                                 number of permits that have been acquired
         */
        @Override
        protected boolean tryReleaseShared(long releases) {
            while (true) {
                long currentAcquisitions = getState();
                long totalAfterRelease = currentAcquisitions - releases;
                if (totalAfterRelease < 0) {
                    // we attempted to release more permits than what was acquired
                    throw new IllegalArgumentException("cannot release " + releases
                                                               + " permits from " + currentAcquisitions + " acquisitions");
                }
                if (compareAndSetState(currentAcquisitions, totalAfterRelease)) {
                    return true;
                }
            }
        }

        /**
         * Tries to increase the limit to the {@code newLimit}. If the {@code newLimit} is lesser
         * than the current limit, then this method returns false. Otherwise, this method will attempt
         * to atomically increase the limit to {@code newLimit}.
         *
         * @param newLimit The new limit to set
         * @return true if the limit was increased to {@code newLimit}. false otherwise
         */
        private boolean tryIncreaseLimitTo(long newLimit) {
            long currentLimit = this.limit.get();
            while (currentLimit < newLimit) {
                if (this.limit.compareAndSet(currentLimit, newLimit)) {
                    return true;
                }
                currentLimit = this.limit.get();
            }
            return false;
        }

        /**
         * {@return the current limit}
         */
        private long currentLimit() {
            return this.limit.get();
        }
    }

    /**
     * A task which iterates over the waiting acquirers and attempt
     * to acquire a permit. If successful, the waiting acquirer(s) (i.e. the CompletableFuture(s))
     * are completed successfully. If not, the waiting acquirers continue to stay in the wait list
     */
    private final class TryAcquireTask implements Runnable {

        @Override
        public void run() {
            Waiter waiter = null;
            while ((waiter = acquirers.peek()) != null) {
                CompletableFuture<Boolean> acquirer = waiter.acquirer;
                if (acquirer.isCancelled() || acquirer.isDone()) {
                    // no longer interested, or already completed, remove it
                    acquirers.remove(waiter);
                    continue;
                }
                if (!tryAcquire()) {
                    // limit reached, no permits available yet
                    break;
                }
                // compose a step which rolls back the acquired permit if the
                // CompletableFuture completed in some other thread, after the permit was acquired.
                acquirer.whenComplete((acquired, t) -> {
                    boolean shouldRollback = acquirer.isCancelled()
                            || t != null
                            || !acquired;
                    if (shouldRollback) {
                        releaseAcquisition();
                    }
                });
                // got a permit, complete the waiting acquirer
                acquirers.remove(waiter);
                try {
                    waiter.executor.execute(() -> acquirer.complete(true));
                } catch (RuntimeException | Error failure) {
                    acquirer.completeExceptionally(failure);
                }
            }
        }
    }
}
