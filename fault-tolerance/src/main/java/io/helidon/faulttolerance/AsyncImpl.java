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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

class AsyncImpl implements Async {
    private final LazyValue<? extends ExecutorService> executor;

    AsyncImpl(Builder builder) {
        this.executor = LazyValue.create(builder.executor());
    }

    @Override
    public <T> Single<T> invoke(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AsyncTask<T> task = new AsyncTask<>(supplier, future);

        try {
            executor.get().submit(task);
        } catch (Throwable e) {
            // rejected execution and other executor related issues
            return Single.error(e);
        }

        return Single.create(future);
    }

    private static class AsyncTask<T> implements Runnable {
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future;

        private AsyncTask(Supplier<T> supplier, CompletableFuture<T> future) {
            this.supplier = supplier;
            this.future = future;
        }

        @Override
        public void run() {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }
    }

    static final class DefaultAsyncInstance {
        private static final Async INSTANCE = Async.builder()
                .build();

        static Async instance() {
            return INSTANCE;
        }
    }
}
