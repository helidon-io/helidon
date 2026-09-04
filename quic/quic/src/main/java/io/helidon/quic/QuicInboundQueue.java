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

package io.helidon.quic;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

/**
 * An inbound queue with one atomic open/offer boundary and exact external byte accounting.
 *
 * @param <T> queued item type
 */
final class QuicInboundQueue<T> {
    private static final int CLOSED = 1 << 31;
    private static final int ACTIVE_MASK = ~CLOSED;

    private final Queue<T> queue;
    private final ToIntFunction<T> size;
    private final IntConsumer account;
    private final IntConsumer release;
    private final AtomicInteger state = new AtomicInteger();
    private final ReentrantLock closeLock = new ReentrantLock();
    private final Condition offersCompleted = closeLock.newCondition();

    QuicInboundQueue(ToIntFunction<T> size, IntConsumer account, IntConsumer release) {
        this(new ConcurrentLinkedQueue<>(), size, account, release);
    }

    QuicInboundQueue(Queue<T> queue,
                     ToIntFunction<T> size,
                     IntConsumer account,
                     IntConsumer release) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.size = Objects.requireNonNull(size, "size");
        this.account = Objects.requireNonNull(account, "account");
        this.release = Objects.requireNonNull(release, "release");
    }

    /**
     * Account and enqueue an item unless close has already acquired queue ownership.
     *
     * @param item item to enqueue
     * @return whether the item was enqueued
     */
    boolean offer(T item) {
        Objects.requireNonNull(item, "item");
        if (!beginOffer()) {
            return false;
        }
        int bytes = -1;
        boolean accounted = false;
        boolean added = false;
        Throwable failure = null;
        try {
            bytes = size.applyAsInt(item);
            if (bytes < 0) {
                throw new IllegalArgumentException("Negative inbound item size: " + bytes);
            }
            account.accept(bytes);
            accounted = true;
            if (!queue.offer(item)) {
                throw new IllegalStateException("Inbound queue rejected an item");
            }
            added = true;
            return true;
        } catch (RuntimeException | Error primaryFailure) {
            failure = primaryFailure;
            throw primaryFailure;
        } finally {
            try {
                if (accounted && !added) {
                    try {
                        release.accept(bytes);
                    } catch (RuntimeException | Error releaseFailure) {
                        if (failure != null && failure != releaseFailure) {
                            failure.addSuppressed(releaseFailure);
                        } else {
                            throw releaseFailure;
                        }
                    }
                }
            } finally {
                endOffer();
            }
        }
    }

    /**
     * Claim the next queued item.
     *
     * @return claimed item, or {@code null}
     */
    T poll() {
        return queue.poll();
    }

    /**
     * Release accounting for a claimed item. The byte count must be captured before processing mutates its buffer.
     *
     * @param bytes queued byte count
     */
    void release(int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Negative inbound release size: " + bytes);
        }
        release.accept(bytes);
    }

    /**
     * Remove and release one exact item after a scheduling failure, unless another owner already claimed it.
     *
     * @param item item offered by the caller
     * @return whether this call removed and released the item
     */
    boolean removeAndRelease(T item) {
        Objects.requireNonNull(item, "item");
        if (!queue.remove(item)) {
            return false;
        }
        release.accept(size.applyAsInt(item));
        return true;
    }

    /**
     * Close the offer boundary, wait for offers that already own it, and release all unclaimed items.
     */
    void closeAndDrain() {
        closeLock.lock();
        try {
            int current = state.get();
            while ((current & CLOSED) == 0 && !state.compareAndSet(current, current | CLOSED)) {
                current = state.get();
            }
            while ((state.get() & ACTIVE_MASK) != 0) {
                offersCompleted.awaitUninterruptibly();
            }
        } finally {
            closeLock.unlock();
        }

        Throwable failure = null;
        T item = queue.poll();
        while (item != null) {
            try {
                release.accept(size.applyAsInt(item));
            } catch (RuntimeException | Error releaseFailure) {
                if (failure == null) {
                    failure = releaseFailure;
                } else if (failure != releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            item = queue.poll();
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }

    boolean isClosed() {
        return (state.get() & CLOSED) != 0;
    }

    private boolean beginOffer() {
        int current = state.get();
        while ((current & CLOSED) == 0) {
            if ((current & ACTIVE_MASK) == ACTIVE_MASK) {
                throw new IllegalStateException("Too many concurrent inbound queue offers");
            }
            if (state.compareAndSet(current, current + 1)) {
                return true;
            }
            current = state.get();
        }
        return false;
    }

    private void endOffer() {
        int updated = state.decrementAndGet();
        if (updated == CLOSED) {
            closeLock.lock();
            try {
                offersCompleted.signalAll();
            } finally {
                closeLock.unlock();
            }
        }
    }
}
