/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Executes {@code onEachComplete} in original order, no matter in which order are supplied completed,
 * completed completables always wait for preceding completable.
 *
 * @param <T> The result type returned by kept futures
 */
class CompletableQueue<T> {

    private static final long MAX_QUEUE_SIZE = 2048;
    private static final long BACK_PRESSURE_LIMIT = MAX_QUEUE_SIZE - (MAX_QUEUE_SIZE >> 2);
    private final ReentrantLock queueLock = new ReentrantLock();
    private final LinkedList<Item<T>> queue = new LinkedList<>();
    private final AtomicLong size = new AtomicLong();
    private volatile BiConsumer<Item<T>, ? super Throwable> onEachComplete;

    private CompletableQueue(final BiConsumer<Item<T>, ? super Throwable> onEachComplete) {
        this.onEachComplete = onEachComplete;
    }

    static <T> CompletableQueue<T> create(BiConsumer<Item<T>, ? super Throwable> onEachComplete) {
        return new CompletableQueue<T>(onEachComplete);
    }

    static <T> CompletableQueue<T> create() {
        return new CompletableQueue<T>(null);
    }

    /**
     * Add {@code BiConsumer} to be invoked in case of completion of completables.
     * All completables added and completed before supplying {@code onEachComplete}
     * are going to be flushed by this method.
     *
     * @param onEachComplete {@code BiConsumer} to be invoked in case of completion of completables in queue.
     */
    void onEachComplete(BiConsumer<Item<T>, ? super Throwable> onEachComplete) {
        this.onEachComplete = onEachComplete;
        tryFlush();
    }

    /**
     * If limit for safe prefetch is reached applying back-pressure is advised.
     *
     * @return true if limit reached
     */
    boolean isBackPressureLimitReached() {
        return BACK_PRESSURE_LIMIT <= size.get();
    }

    /**
     * Add completable to the queue, if completed {@code onEachComplete} is invoked only after all older completables
     * invoked {@code onEachComplete}.
     * If {@code onEachComplete} is not provided, all completables has to wait till {@code onEachComplete method is invoked}.
     *
     * @param future   to be added to queue
     * @param metadata to be propagated to {@code onEachComplete} consumer
     */
    void add(CompletableFuture<T> future, Object metadata) {
        try {
            queueLock.lock();
            queue.add(Item.create(future, metadata));
            if (size.incrementAndGet() > MAX_QUEUE_SIZE) {
                throw ExceptionUtils.createCompletableQueueOverflow(MAX_QUEUE_SIZE);
            }
            future.whenComplete((t, u) -> tryFlush());
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Add completable to the queue, if completed {@code onEachComplete} is invoked only after all older completables
     * invoked {@code onEachComplete}.
     * If {@code onEachComplete} is not provided, all completables has to wait till {@code onEachComplete method is invoked}.
     *
     * @param future to be added to queue
     */
    void add(CompletableFuture<T> future) {
        this.add(future, null);
    }

    private void tryFlush() {
        try {
            queueLock.lock();
            var onEachComplete = this.onEachComplete;
            try {
                if (onEachComplete == null) return;
                while (!queue.isEmpty() && queue.getFirst().getCompletableFuture().isDone()) {
                    var item = queue.poll();
                    if (Objects.isNull(item)) return;
                    size.decrementAndGet();
                    item.setValue(item.getCompletableFuture().get());
                    onEachComplete.accept(item, null);
                }
                return;
            } catch (InterruptedException | ExecutionException e) {
                onEachComplete.accept(null, e);
            }
        } finally {
            queueLock.unlock();
        }
    }

    static class Item<T> {
        private final CompletableFuture<T> completableFuture;
        private final Object metadata;
        private T value;

        private Item(final CompletableFuture<T> completableFuture, final Object metadata) {
            this.completableFuture = completableFuture;
            this.metadata = metadata;
        }

        static <T> Item<T> create(CompletableFuture<T> completableFuture) {
            return new Item<>(completableFuture, null);
        }

        static <T> Item<T> create(CompletableFuture<T> completableFuture, Object metadata) {
            return new Item<>(completableFuture, metadata);
        }

        CompletableFuture<T> getCompletableFuture() {
            return completableFuture;
        }

        Object getMetadata() {
            return metadata;
        }

        T getValue() {
            return value;
        }

        void setValue(final T value) {
            this.value = value;
        }
    }
}
