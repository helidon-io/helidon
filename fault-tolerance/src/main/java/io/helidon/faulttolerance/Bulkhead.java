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
 */

package io.helidon.faulttolerance;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

public class Bulkhead implements Handler {
    private final Queue<Enqueued<?>> queue;
    private final Semaphore inProgress;

    private Bulkhead(Builder builder) {
        this.inProgress = new Semaphore(builder.limit, true);
        if (builder.queueLength == 0) {
            queue = new NoQueue();
        } else {
            this.queue = new LinkedBlockingQueue<>(builder.queueLength);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        if (inProgress.tryAcquire()) {
            CompletionStage<T> result = supplier.get();

            result.handle((it, throwable) -> {
                // we still have an acquired semaphore
                Enqueued<?> polled = queue.poll();
                while (polled != null) {
                    invokeEnqueued((Enqueued<Object>) polled);
                    polled = queue.poll();
                }
                inProgress.release();
                return null;
            });

            return Single.create(result);
        } else {
            Enqueued<T> enqueued = new Enqueued<>(supplier);
            if (!queue.offer(enqueued)) {
                return Single.error(new BulkheadException("Bulkhead queue is full"));
            }
            return Single.create(enqueued.future());
        }
    }

    private void invokeEnqueued(Enqueued<Object> enqueued) {
        CompletableFuture<Object> future = enqueued.future();
        CompletionStage<Object> completionStage = enqueued.originalStage();
        completionStage.thenAccept(future::complete);
        completionStage.exceptionally(throwable -> {
            future.completeExceptionally(throwable);
            return null;
        });
    }

    private static class Enqueued<T> {
        private LazyValue<CompletableFuture<T>> resultFuture = LazyValue.create(CompletableFuture::new);
        private Supplier<? extends CompletionStage<T>> supplier;

        private Enqueued(Supplier<? extends CompletionStage<T>> supplier) {
            this.supplier = supplier;
        }

        private CompletableFuture<T> future() {
            return resultFuture.get();
        }

        private CompletionStage<T> originalStage() {
            return supplier.get();
        }
    }

    public static class Builder implements io.helidon.common.Builder<Bulkhead> {
        private int limit;
        private int queueLength = 10;

        private Builder() {
        }

        @Override
        public Bulkhead build() {
            return new Bulkhead(this);
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder queueLength(int queueLength) {
            this.queueLength = queueLength;
            return this;
        }
    }

    private static class NoQueue extends ArrayDeque<Enqueued<?>> {
        @Override
        public boolean offer(Enqueued<?> enqueued) {
            return false;
        }

        @Override
        public Enqueued<?> poll() {
            return null;
        }
    }
}
